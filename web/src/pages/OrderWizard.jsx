// Owner: Zihang Cao (recommendation + order creation).
// Wireframes 04–07 are ONE route with 4 internal steps.
import PagePlaceholder from '../components/PagePlaceholder';
export default function OrderWizard() {
    return (<PagePlaceholder owner="Zihang Cao" page="Order Wizard (4 steps, 1 route)" wireframe="wireframes/04..07 (addresses -> package -> candidates -> pay)" apis={[
            'getRecommendations({ pickup, dropoff, package, priority })  ->  POST /api/recommendations',
            'createOrder({ ..., paymentMethodId })  ->  POST /api/orders  (pay + create in ONE call)',
            'getStations()  ->  GET /api/stations  (response is contract-TBD)',
            'useWizard()  (the draft that survives between steps; Dashboard AI prefill lands here)',
        ]} todos={[
            'Step 1 addresses: line1 + zip (city fixed to San Francisco); optional <MapView/> click-to-pick filling lat/lng',
            'Step 2 package: description / weight / dimensions / priority (STANDARD | EXPRESS)',
            'Step 3 candidates: card per candidate with isFastest / isCheapest tags, stationName, VehicleIcon; disable availableUnits = 0',
            'Step 4 review + pay: order summary card; mock payment sends paymentMethodId = mock_card_<last4>',
            "On 201: navigate to /tracking/:orderId; on failure surface payment vs. order errors distinctly (shapes are contract-TBD)",
        ]}/>);
}
