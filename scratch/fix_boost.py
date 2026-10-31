with open('app/src/main/java/com/music/vikify/data/firebase/FirebaseSyncManager.kt', 'r', encoding='utf-8') as f:
    fc = f.read()

fc = fc.replace('isBoosted: Boolean', 'isMore: Boolean')
fc = fc.replace('if (isBoosted)', 'if (isMore)')
fc = fc.replace('"boosted"', '"more"')

with open('app/src/main/java/com/music/vikify/data/firebase/FirebaseSyncManager.kt', 'w', encoding='utf-8') as f:
    f.write(fc)

with open('app/src/main/java/com/music/vikify/ui/MainViewModel.kt', 'r', encoding='utf-8') as f:
    mc = f.read()

mc = mc.replace('TrackPreference.BOOST', 'com.music.vikify.data.personalization.TrackPreference.MORE')

with open('app/src/main/java/com/music/vikify/ui/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(mc)

print('Updated FirebaseSyncManager and MainViewModel successfully')
