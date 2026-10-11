# Portfolio site — bose.androidplay.in

Static personal site, hosted on **Cloudflare Pages** (project `ankush-bose`).
It is independent of the Ktor API — nothing here touches Cloud Run.

| What | Where |
|---|---|
| Live site | https://bose.androidplay.in |
| Fallback URL | https://ankush-bose.pages.dev |
| Source (deployed) | `portfolio-v2/` — `index.html` + `assets/` |
| Alternate design (not deployed) | `portfolio/` (v1) |
| DNS | Hostinger: `CNAME bose → ankush-bose.pages.dev` |

> Everything inside `portfolio-v2/` is published. Keep notes and drafts out of it.

The commands below use `make -C "$(git rev-parse --show-toplevel)"` so they work from any
folder inside the repo (the Makefile lives at the repo root). From the root, plain
`make <target>` works too.

## Deploy

```bash
make -C "$(git rev-parse --show-toplevel)" deploy-portfolio
```

Takes a few seconds. The first time on a new machine, run `npx wrangler login` once.

## Update the résumé

```bash
make -C "$(git rev-parse --show-toplevel)" portfolio-resume FILE=~/Downloads/Resume_AnkushBose.pdf
```

Copies the PDF to `portfolio-v2/assets/Ankush_Bose_Resume.pdf` and deploys.
If browsers keep showing the old file, bump the `?v=` on the résumé links in
`index.html` (e.g. `assets/Ankush_Bose_Resume.pdf?v=2`).

## Live Medium post ("Latest on Medium" card, left column under the links)

`portfolio-v2/_worker.js` is a Cloudflare Pages advanced-mode worker. It serves
`/api/medium` — the latest posts from `https://medium.com/feed/@bosankus` as JSON,
cached at the edge for an hour — and passes every other request to the static site.
Pages never serves `_worker.js` itself.

New Medium posts appear on the site automatically within an hour; no redeploy needed.
The card shows the newest post. `index.html` carries a built-in copy of it, which stays on screen if the feed
can't be reached. Refresh that copy now and then so the fallback isn't stale.

Plain `python3 -m http.server` doesn't run the worker (you'll only see the built-in post).
To preview with the live feed:

```bash
npx wrangler pages dev "$(git rev-parse --show-toplevel)/portfolio-v2" --port 8788
```

## Photo protection

- The full-size cut-out lives at `portfolio-og/source/ankush-master.png` — **local only**,
  git-ignored (the repo is public). Keep a backup of it somewhere safe.
- The site ships `portfolio-v2/assets/portrait.png`, resized to 720px tall (just enough for
  retina at the largest display size). Regenerate after changing the master:
  `sips -Z 720 portfolio-og/source/ankush-master.png --out portfolio-v2/assets/portrait.png`
- `_worker.js` returns 403 for `/assets/portrait.png` when it's opened directly in a tab or
  embedded by another site, and marks it `noindex, noimageindex`.
- The page blocks right-click, drag and iOS long-press save on the photo.

Nothing stops DevTools or a screenshot — this raises the effort, it can't make copying impossible.

## Link preview image (WhatsApp, LinkedIn, X)

The share card is `portfolio-v2/assets/og.jpg`, rendered from `portfolio-og/card.html`
(name, photo block, role card and a QR code to the site). After editing the card:

```bash
make -C "$(git rev-parse --show-toplevel)" portfolio-og
```

Then bump `?v=` on both `og:image` and `twitter:image` in `portfolio-v2/index.html` and
deploy. Chat apps cache previews per URL, so to test a fresh preview share
`https://bose.androidplay.in/?v=2` (any new query string).

## Preview locally

```bash
python3 -m http.server 8766 --directory "$(git rev-parse --show-toplevel)/portfolio-v2"
```

Then open http://localhost:8766.

## Edit content

All copy lives in `portfolio-v2/index.html`:

- Name / card text / profile bio — top of `<body>`
- Experience, education, skills, open source — further down, plain HTML
- Photo — `assets/ankush.png` (background-removed cut-out)

## Deploy v1 instead (or as well)

v1 would need its own Pages project and DNS record, e.g. `me.androidplay.in`:

```bash
make -C "$(git rev-parse --show-toplevel)" deploy-portfolio PORTFOLIO_DIR=portfolio PORTFOLIO_PROJECT=ankush-me
```

(create the project first with `npx wrangler pages project create ankush-me --production-branch main`,
then add the custom domain in the dashboard and a `CNAME me → ankush-me.pages.dev` at Hostinger).
