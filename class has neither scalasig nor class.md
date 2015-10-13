#### problem

class class **SOME CLASS** is missing both @ScalaSig and .class file!

I checked this problem and message is some kind of lie.
Problem really was that proguard removed ScalaSig class attribute.

#### solution

```
-keepattributes Signature,*Annotation*
# I'm not sure if Signature is required in this situation
```