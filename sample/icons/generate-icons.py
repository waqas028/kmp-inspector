"""Regenerate launcher assets: Python with Pillow, Node with sharp (NODE_PATH)."""
from pathlib import Path
import json, re, subprocess, io
from PIL import Image

root = Path(__file__).resolve().parents[2]
icons = root / 'sample/icons'
source = (root / 'library/src/commonMain/kotlin/com/waqas028/kmpinspector/presentation/theme/InspectorIcons.kt').read_text()
path = re.search(r'private const val TRAVEL_EXPLORE_PATH_DATA =\s*"([^"]+)"', source)[1]
# Keep the existing Material symbol comfortably within Android's adaptive safe zone.
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 108 108">
<rect width="108" height="108" fill="#232221"/>
<g transform="translate(25 25) scale(0.0604166667) translate(0 960)"><path fill="#E1AD66" d="{path}"/></g>
</svg>\n'''
(icons / 'app-icon.svg').write_text(svg)
png = subprocess.check_output(['node', '-e', "require('sharp')(process.argv[1]).png().toBuffer().then(b=>process.stdout.write(b))", str(icons / 'app-icon.svg')])
im = Image.open(io.BytesIO(png)).convert('RGB')
def save(rel, size):
    dest = root / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.resize((size, size), Image.Resampling.LANCZOS).save(dest)

save('sample/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png', 1024)
save('sample/shared/src/jvmMain/resources/app-icon.png', 512)
save('sample/icons/app-icon.png', 512)
im.save(icons / 'app-icon.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
im.save(icons / 'app-icon.icns')
res = root / 'sample/androidApp/src/main/res'
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    save(f'sample/androidApp/src/main/res/mipmap-{density}/ic_launcher.png', size)
(res / 'drawable').mkdir(parents=True, exist_ok=True)
(res / 'drawable/ic_launcher_foreground.xml').write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="25" android:translateY="25" android:scaleX="0.0604166667" android:scaleY="0.0604166667">
        <group android:translateY="960">
            <path android:fillColor="#E1AD66" android:pathData="{path}" />
        </group>
    </group>
</vector>\n''')
for qualifier, monochrome in [('v26',False), ('v33',True)]:
    folder = res / f'mipmap-anydpi-{qualifier}'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'ic_launcher.xml').write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
''' + ('    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n' if monochrome else '') + '</adaptive-icon>\n')
(res / 'values').mkdir(parents=True, exist_ok=True)
(res / 'values/ic_launcher_colors.xml').write_text('<resources>\n    <color name="ic_launcher_background">#232221</color>\n</resources>\n')
catalog = root / 'sample/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json'
data = json.loads(catalog.read_text())
data['images'][0]['filename'] = 'AppIcon.png'
catalog.write_text(json.dumps(data, indent=2) + '\n')
