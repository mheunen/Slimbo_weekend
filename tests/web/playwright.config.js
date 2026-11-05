const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: ".",
  testMatch: "*.spec.js",
  use: { baseURL: "http://localhost:4173", serviceWorkers: "block" },
  webServer: { command: "python3 -m http.server 4173 --directory ../../web", url: "http://localhost:4173/app/index.html", reuseExistingServer: true },
});
