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
  (JNIEnv * jenv, jclass jcls, jbyteArray jbuf_name, jint ncodes, jintArray jcodes,
   jint nresc, jobjectArray jnames, jintArray jtypes, jobjectArray jrescs, jintArray jcounts, jintArray jfieldwidths, jlongArray jids)
{
    vx_obj_opcodes_t * v = vx_obj_opcodes_create(ncodes, nresc);


    // Grab buffer name
    jbyte *buf_name_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jbuf_name, NULL);
    char * buf_name = strdup(buf_name_env);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuf_name, buf_name, 0);

    printf("Buffer name: %s\n", (char *)buf_name);


    // Copy over the Opcodes and integer parameters
    jint * codes_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcodes, NULL);
    memcpy(v->codes, codes_env, sizeof(uint32_t) * ncodes);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcodes, codes_env, 0);


    jint * types_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jtypes, NULL);
    jint * counts_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcounts, NULL);
    jint * fieldwidths_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jfieldwidths, NULL);
    jlong * ids_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jids, NULL);
    for (int i = 0; i < nresc; i++) {
        vx_resc_t * vr = malloc(sizeof(vx_resc_t));

        // primitive fields
        vr->type = types_env[i];
        vr->count = counts_env[i];
        vr->id = ids_env[i];



        jobject jres  = (*jenv)->GetObjectArrayElement(jenv, jrescs, i);

        jbyte* res = (*jenv)->GetPrimitiveArrayCritical(jenv, jres, NULL);


    }
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jtypes, types_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcounts, counts_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jfieldwidths, fieldwidths_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jids, ids_env, 0);

    /* jint *codes_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcodes, NULL); */





    //XXX Free buf_name, since it doesn't go in the struct!!!
    return 0;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_fbo_1create
  (JNIEnv * jenv, jclass jcls, jint width, jint height)
{
    return fbo_create(width, height);
}
