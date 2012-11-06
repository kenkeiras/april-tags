#include "april_vx_VxLocalServer.h"

#include "vx.h"


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_gl_1initialize
  (JNIEnv *jenv, jclass jcls)
{
    return init_glcontext();
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_update_1buffer
  (JNIEnv * jenv, jclass jcls, jstring jstr)
{

    const char * str = (*jenv)->GetStringUTFChars(jenv, jstr, NULL);
    printf("Buffer name: %s\n", str);
    (*jenv)->ReleaseStringUTFChars(jenv, jstr, str);

    return 0;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_fbo_1create
  (JNIEnv * jenv, jclass jcls, jint width, jint height)
{
    return fbo_create(width, height);
}
