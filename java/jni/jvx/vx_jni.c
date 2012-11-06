#include "glcontext.h"


#include "april_vx_VxLocalServer.h"

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_update_1buffer
  (JNIEnv * jenv, jclass jcls, jstring jstr)
{

    const char * str = (*jenv)->GetStringUTFChars(jenv, jstr, NULL);
    printf("Buffer name: %s\n", str);
    (*jenv)->ReleaseStringUTFChars(jenv, jstr, str);
}
