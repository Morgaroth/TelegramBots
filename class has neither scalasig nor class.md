#### problem

class class **SOME CLASS** is missing both @ScalaSig and .class file!

I checked this problem and message is some kind of lie.
Problem really was that proguard removed ScalaSig class, not any attibute of
WSPOMNIANEJ class

#### resolve

```
-keep class scala. ScalaSig
```