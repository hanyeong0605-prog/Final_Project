import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router";
import { InterestProvider } from "./features/interests/model/InterestContext";
import { AuthProvider } from "./features/auth/model/AuthContext";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthProvider><InterestProvider><RouterProvider router={router} /></InterestProvider></AuthProvider>
  </StrictMode>,
);
