package main

import (
	"encoding/json"
	"html/template"
	"net/http"
	"os"
	"regexp"
	"strings"
)

var (
	trackID            = regexp.MustCompile("^[A-Za-z0-9_-]{11}$")
	signingFingerprint = regexp.MustCompile("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")
	sharedTrackPage    = template.Must(template.New("track").Parse(`<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Listen on Vikify</title>
<main>
<h1>Listen on Vikify</h1>
<p><a href="{{.AppURL}}">Open in Vikify</a></p>
<p><a href="https://music.youtube.com/watch?v={{.ID}}">Listen on YouTube Music</a></p>
</main>
</html>`))
)

func handleSharedTrack(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if !trackID.MatchString(id) {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = sharedTrackPage.Execute(w, struct {
		ID string
		AppURL template.URL
	}{ID: id, AppURL: template.URL("vikify://track/" + id)})
}

func handleAppAssociation(w http.ResponseWriter, r *http.Request) {
	var fingerprints []string
	for _, value := range strings.Split(os.Getenv("VIKIFY_ANDROID_CERT_SHA256"), ",") {
		value = strings.ToUpper(strings.TrimSpace(value))
		if signingFingerprint.MatchString(value) {
			fingerprints = append(fingerprints, value)
		}
	}
	if len(fingerprints) == 0 {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode([]map[string]any{{
		"relation": []string{"delegate_permission/common.handle_all_urls"},
		"target": map[string]any{
			"namespace":                "android_app",
			"package_name":             "com.music.vikify",
			"sha256_cert_fingerprints": fingerprints,
		},
	}})
}
