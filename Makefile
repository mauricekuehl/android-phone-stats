export JAVA_HOME := /Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home

.PHONY: deploy build clean release

deploy:
	./gradlew installDebug

build:
	./gradlew assembleDebug

release:
	./gradlew assembleRelease

clean:
	./gradlew clean
