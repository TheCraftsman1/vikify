"use strict";

const heading = document.getElementById("heading");
const description = document.getElementById("description");
const openApp = document.getElementById("open-app");
const youtube = document.getElementById("youtube");
const track = /^\/(?:track|song)\/([A-Za-z0-9_-]{11})\/?$/.exec(location.pathname);
const invite = /^\/invite\/([A-Za-z0-9]{6})$/.exec(location.pathname);

if (track) {
  heading.textContent = "A song for you.";
  description.textContent = "Open the shared song in Vikify.";
  openApp.href = `vikify://track/${track[1]}`;
  openApp.hidden = false;
  youtube.href = `https://music.youtube.com/watch?v=${track[1]}`;
  youtube.hidden = false;
} else if (invite) {
  heading.textContent = "Listen together.";
  description.textContent = "Open this invitation in Vikify to join the listening session.";
  const appUrl = new URL(`vikify://party/${invite[1].toUpperCase()}`);
  const server = new URLSearchParams(location.search).get("server");
  if (server) {
    try {
      const address = new URL(server);
      if (address.protocol === "https:" && !address.username && !address.password) {
        appUrl.searchParams.set("server", address.href);
      }
    } catch {
      // An invalid optional address leaves the invitation on the app's default server.
    }
  }
  openApp.href = appUrl.href;
  openApp.hidden = false;
} else if (location.pathname !== "/") {
  heading.textContent = "Check this link.";
  description.textContent = "This link has an incomplete song or invitation address. Ask the sender to share it again.";
}
