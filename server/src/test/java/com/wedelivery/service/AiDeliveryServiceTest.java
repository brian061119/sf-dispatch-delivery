package com.wedelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDeliveryServiceTest {
    ObjectMapper mapper = new ObjectMapper();
    GeminiDeliveryClient gemini = mock(GeminiDeliveryClient.class);
    RecommendationService recommendations = mock(RecommendationService.class);
    TrackingService tracking = mock(TrackingService.class);
    AiDeliveryService service = new AiDeliveryService(gemini, recommendations, tracking, mapper);
    @Test void extractsAllDraftFieldsAndIgnoresInventedCoordinates() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"DRAFT\",\"pickupText\":\"Market St\",\"dropoffText\":\"Mission St\",\"weightKg\":2,\"fragile\":true,\"priority\":\"EXPRESS\",\"lat\":37.7}"));
        Map<?,?> prefill=(Map<?,?>)service.parse("send a 2kg item from Market St to Mission St, fragile and express").get("prefill");
        assertThat(prefill.get("priority")).isEqualTo("EXPRESS");
        assertThat(((Map<?,?>)prefill.get("pickup")).get("line1")).isEqualTo("Market St");
        assertThat(((Map<?,?>)prefill.get("pickup")).containsKey("lat")).isFalse();
        assertThat(((Map<?,?>)prefill.get("pkg")).get("fragile")).isEqualTo(true);
    }
    @Test void missingWeightIsNotInvented() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"DRAFT\"}"));
        Map<String,Object> result=service.parse("send a package");
        assertThat(((List<?>)result.get("missingFields")).contains("weightKg")).isTrue();
        assertThat(((Map<?,?>)((Map<?,?>)result.get("prefill")).get("pkg")).containsKey("weightKg")).isFalse();
    }
    @Test void quoteWithWeightOnlyAsksForAddressesAndNeverCallsPricing() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"weightKg\":2}"));
        assertThat(service.chat("How much for a 2kg package?", List.of(), new User()).get("reply").toString()).contains("exact pickup");
        verifyNoInteractions(recommendations);
    }
    @Test void ambiguousStreetCannotProduceQuote() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"pickupText\":\"Market St\",\"dropoffText\":\"Mission St\",\"weightKg\":2}"));
        assertThat(service.chat("quote", List.of(), new User()).get("reply").toString()).contains("could not verify");
        verifyNoInteractions(recommendations);
    }
    @Test void trackingUsesRealServiceAndDoesNotInventPrice() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"TRACK\",\"trackingCode\":\"ABC123456789ABCD\"}"));
        when(tracking.trackByTrackingCode("ABC123456789ABCD")).thenReturn(TrackingResponse.builder().orderNumber("ORD-1").orderStatus(OrderStatus.IN_TRANSIT).etaMinutesRemaining(8).build());
        Map<String,Object> result=service.chat("track ABC123456789ABCD", List.of(), new User());
        assertThat(result.get("reply").toString()).contains("IN_TRANSIT", "8 min");
        assertThat(((Map<?,?>)((List<?>)result.get("cards")).get(0)).containsKey("estimatedCost")).isFalse();
    }
    @Test void unknownCodeReturnsHelpfulResponse() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"TRACK\",\"trackingCode\":\"ABC123456789ABCD\"}"));
        when(tracking.trackByTrackingCode(anyString())).thenThrow(new ResourceNotFoundException("missing"));
        assertThat(service.chat("track", List.of(), new User()).get("reply").toString()).contains("No delivery found");
    }
    @Test void rejectsInvalidWeightAndBlankInput() throws Exception {
        assertThatThrownBy(()->service.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"DRAFT\",\"weightKg\":-2}"));
        assertThatThrownBy(()->service.parse("send -2kg")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void forwardsHistoryForFollowupAndRejectsWrites() throws Exception {
        List<Map<String,String>> history=List.of(Map.of("role","user","content","quote 2kg"));
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"WRITE\"}"));
        assertThat(service.chat("cancel it", history, new User()).get("reply").toString()).contains("cannot execute");
        verify(gemini).extract("cancel it", history); verifyNoInteractions(tracking,recommendations);
    }
    @Test void verifiedQuoteUsesCurrentUserAndRealRecommendationService() throws Exception {
        AiDeliveryService spy = spy(service);
        User vip = new User();
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"pickupText\":\"415 Mission St\",\"dropoffText\":\"2800 Mission St\",\"weightKg\":2}"));
        doReturn(Map.of("line1","415 Mission St","lat",37.7897,"lng",-122.3972)).when(spy).locate(argThat(a -> "415 Mission St".equals(a.get("line1"))));
        doReturn(Map.of("line1","2800 Mission St","lat",37.7524,"lng",-122.4184)).when(spy).locate(argThat(a -> "2800 Mission St".equals(a.get("line1"))));
        when(recommendations.generateContractRecommendations(anyMap(), same(vip))).thenReturn(
            com.wedelivery.dto.RecommendationContractDto.Response.builder().candidates(List.of()).build());
        assertThat(spy.chat("quote",List.of(),vip).get("reply").toString()).contains("No delivery plan");
        verify(recommendations).generateContractRecommendations(argThat(body -> ((Map<?,?>)body.get("pickup")).containsKey("lat")), same(vip));
    }

    @Test void unknownDimensionsReturnedAsZeroDoNotBreakWeightOnlyQuote() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"weightKg\":2,\"lengthCm\":null,\"widthCm\":0,\"heightCm\":0}"));
        Map<String,Object> result=service.chat("please quote my package",List.of(),new User());
        assertThat(result.get("reply").toString()).contains("exact pickup");
        Map<?,?> pkg=(Map<?,?>)((Map<?,?>)result.get("prefill")).get("pkg");
        assertThat(pkg.containsKey("widthCm")).isFalse();
        assertThat(pkg.containsKey("heightCm")).isFalse();
        verifyNoInteractions(recommendations);
    }

    @Test void weightOnlyQuestionWorksWithoutModelAndKeepsRouteFollowupsOnModel() throws Exception {
        assertThat(service.chat("How much for a 2kg package?",List.of(),new User()).get("reply").toString()).contains("exact pickup");
        verifyNoInteractions(gemini);
        List<Map<String,String>> history=List.of(Map.of("role","user","content","from 415 Mission St to 2800 Mission St"));
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"QUOTE\",\"weightKg\":2}"));
        service.chat("How much for a 2kg package?",history,new User());
        verify(gemini).extract("How much for a 2kg package?",history);
    }

    @Test void helpReturnsSpecificModelAnswerInsteadOfGenericCapabilityList() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"HELP\",\"reply\":\"There are two carrier types: ground robots and drones. Available plans depend on your route and package.\"}"));
        assertThat(service.chat("how many ways to deliver items",List.of(),new User()).get("reply").toString())
            .contains("two carrier types").doesNotContain("Tell me which you need");
        verifyNoInteractions(recommendations,tracking);
    }
    @Test void policyQuestionsReturnKnowledgeAnswerWithoutExecutingWrites() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"HELP\",\"reply\":\"No. Cancellation is not allowed after package pickup.\"}"));
        assertThat(service.chat("Can I cancel after pickup?",List.of(),new User()).get("reply").toString()).contains("not allowed after");
        verifyNoInteractions(recommendations,tracking);
    }
    @Test void missingHelpAnswerCannotSilentlyBecomeGenericReply() throws Exception {
        when(gemini.extract(anyString(), anyList())).thenReturn(mapper.readTree("{\"intent\":\"HELP\"}"));
        assertThatThrownBy(()->service.chat("how to set pickup",List.of(),new User())).hasMessageContaining("no answer");
    }

}
