# hello-sensing

An example Android sensing application together with some feature computation code.

## How to use

Note: You might need to add the path to your Android SDK in a `local.properties` file, but you'll get notified of that as an error. See below.

1. Get the source with `git clone git@github.com:ubi-cs-au-dk/hello-sensing.git`.
2. Change to the new directory.
3. To install the Android app: Run `./gradlew :hello-sensing-android:inDeFlDe` on Mac/Linux, or `gradlew.bat :hello-sensing-android:inDeFlDe` on Windows.
4. To test the feature computation code: Run `./gradlew :hello-sensing-ml:inAp` on Mac/Linux, then `./hello-sensing-ml/build/install/hello-sensing-ml/bin/hello-sensing-ml <example-data/AccelerometerEvent.csv >example-data/AccelerometerFeatures.csv`. It should be similar on Windows.

You can then load the `AccelerometerFeatures.csv` file into Weka. Note that you should add your own class labels to each instance, probably by changing the code in the `dk.au.cs.ubi.hellosensing.FeatureComputer.java` in `hello-sensing-ml`.

## local.properties

The file looks like this on my machine (Mac OS X):

    sdk.dir=/Applications/Android Studio.app/sdk

## Questions? Comments?

For questions and comments, please post on the webboard: https://services.brics.dk/java/courseadmin/CAC13/webboard/forum?forum=702 . If you believe you've found a bug or want a certain feature, feel free to open an issue here on GitHub: https://github.com/ubi-cs-au-dk/hello-sensing/issues .

## Does santa exist?

No.