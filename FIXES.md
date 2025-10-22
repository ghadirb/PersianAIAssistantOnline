# Remaining Fixes

## Fix 3: Draw all routes on map
- Change showRoutes() to draw all routes with different colors
- Route 1: Blue (#4285F4)
- Route 2: Green (#34A853)  
- Route 3: Orange (#FBBC04)

## Fix 4: Click on route line
- Make polylines clickable
- onClick -> call Android with route index

## Fix 5: Show choice after click
- Remove intermediate dialog
- Click route -> show "بزن بریم / Google Maps"

## Fix 6: Persian TTS
- Force Persian locale in PersianVoiceAlerts
- Check TTS data installed

Implementation in next commit...
