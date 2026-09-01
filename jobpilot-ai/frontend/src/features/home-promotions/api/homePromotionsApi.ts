import { getJson } from "../../../api/httpClient";

export type HomePromotion = {
  id: number;
  slotType: "TRAINING" | "BOOK";
  title: string;
  provider: string | null;
  description: string | null;
  imageUrl: string | null;
  targetUrl: string;
};

export const getHomePromotions = () => getJson<HomePromotion[]>("/api/v1/home-promotions");
