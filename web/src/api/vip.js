import { http } from "../lib/http";

export async function getVipStatus() {
  const { data } = await http.get("/vip/status");
  return data;
}

export async function subscribeVip(payload) {
  const { data } = await http.post("/vip/subscribe", payload);
  return data;
}
