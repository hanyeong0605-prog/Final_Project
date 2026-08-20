import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";

const MAX_AGE_MS = 10 * 60 * 1000;

/** Requires a fresh face verification in this browser tab, not merely an admin JWT. */
export function RequireAdminFaceVerified({ children }: PropsWithChildren) {
  const verifiedAt = Number(sessionStorage.getItem("admin_face_verified_at"));
  return Number.isFinite(verifiedAt) && Date.now() - verifiedAt < MAX_AGE_MS
    ? children
    : <Navigate to="/admin" replace />;
}
