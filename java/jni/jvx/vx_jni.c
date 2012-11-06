#include "april_vx_VxLocalServer.h"

#include "vx.h"
#include "vx_obj_opcodes.h"
#include "string.h"

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_gl_1initialize
  (JNIEnv *jenv, jclass jcls)
{
    return init_glcontext();
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_update_1buffer
(JNIEnv * jenv, jclass jcls, jbyteArray jbuf_name, jint ncodes, jintArray jcodes, jint nstrs, jobjectArray strs, jint nresc, jintArray types, jobjectArray rescArrs, jintArray lengths, jlongArray jids)
{
    vx_obj_opcodes_t * v = vx_obj_opcodes_create(ncodes, nresc);


    jbyte *buf_name_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jbuf_name, NULL);
    char * buf_name = strdup(buf_name_env);

    printf("Buffer name: %s\n", (char *)buf_name);


    /* jint *codes_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcodes, NULL); */



    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuf_name, buf_name, 0);


    //XXX Free buf_name, since it doesn't go in the struct!!!
    return 0;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_fbo_1create
  (JNIEnv * jenv, jclass jcls, jint width, jint height)
{
    return fbo_create(width, height);
}
