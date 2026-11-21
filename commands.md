./add-version forge 1.21.1

./gradlew buildAndCollectAll

./gradlew buildAndCollectFabric

./gradlew -Pmod.loaders=fabric :fabric:1.21.4:runClient
