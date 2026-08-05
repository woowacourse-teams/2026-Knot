import React from "react";
import { createRoot } from "react-dom/client";

createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <div>Hello, World!</div>
  </React.StrictMode>,
);

console.log("Hello, World!");
