import { App } from "@/app/app";
import "@/index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

const container = document.getElementById("root");
if (!container) {
  throw new Error("Thiếu thẻ #root trong index.html");
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
