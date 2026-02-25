# Backend OCR Import

## Environment Variables

- `BAIDU_OCR_APP_ID`
- `BAIDU_OCR_API_KEY`
- `BAIDU_OCR_SECRET_KEY`

## Endpoints

- `POST /api/ocr/recognize` (multipart, fields: `files` or `file`, `strategyId`, optional `brokerType`)
- `POST /api/ocr/rematch` (JSON body: `strategyId`, `records`)
- `POST /api/ocr/import` (JSON body: `strategyId`, `records`)

## Notes

- OCR matching tolerance is controlled by `ocr.match.tolerance-percent` in `backend/src/main/resources/application.yml`.
- OCR duplicate time window uses `ocr.match.time-window-seconds` in `backend/src/main/resources/application.yml`.
- OCR rematch supports manual opening/closing flags to recompute grid matching.
