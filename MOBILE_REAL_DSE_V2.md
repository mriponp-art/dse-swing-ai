# DSE Swing AI — Mobile-Only Real DSE v2

This release adds a native Android-first scan path so the app no longer depends on a private backend for the first full workflow.

## Mobile flow
Android native HTTPS → current DSE universe → per-symbol historical OHLCV → local feature calculation → Breakout/Pullback → hard gates → Top 10/A+.

## Hard gates
- Missing/insufficient historical data is not promoted.
- Minimum R:R is 1:2.
- Risk planning is not allowed to override missing data.
- No broker execution.

## Data sources
Primary: unofficial structured DSE crawler proxy (`bdstock.org`). Fallback probes: public DSE endpoints. The source is not treated as authoritative truth.

## Important limitation
This build has not been compiled or live-tested on a physical Android device in this environment. A live response may vary because the sources are unofficial/public endpoints.
