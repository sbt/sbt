#include <jni.h>
#include "sbt_JniLibrary.h"

extern "C" {

/*
 * Class:     sbt_JniLibrary
 * Method:    getIntegerValue
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sbt_JniLibrary_getIntegerValue
  (JNIEnv *env, jobject obj) {
    return 1;
  }
}
