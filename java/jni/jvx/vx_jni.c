#include "april_vx_VxLocalServer.h"
#include <malloc.h>

#include "vx.h"
#include "vx_code_input_stream.h"
#include "string.h"

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_gl_1initialize
  (JNIEnv *jenv, jclass jcls)
{
    return vx_init();
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_update_1buffer
 (JNIEnv * jenv, jclass jcls, jbyteArray jbuf_name, jint codes_len, jbyteArray jcodes,
   jint nresc, jintArray jtypes, jobjectArray jrescs, jintArray jcounts, jintArray jfieldwidths, jlongArray jids)
{

    // Grab buffer name
    jbyte *buf_name_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jbuf_name, NULL);
    char * buf_name = strdup((char*)buf_name_env);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuf_name, buf_name, 0);

    // Copy over the Opcodes and integer parameters
    jbyte * codes_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcodes, NULL);
    vx_code_input_stream_t * codes = vx_code_input_stream_init((uint8_t *)codes_env, (uint32_t)codes_len);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcodes, codes_env, 0);


    vx_resc_t ** resources = malloc(sizeof(vx_resc_t*)*nresc);

    jint * types_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jtypes, NULL);
    jint * counts_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcounts, NULL);
    jint * fieldwidths_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jfieldwidths, NULL);
    jlong * ids_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jids, NULL);
    for (int i = 0; i < nresc; i++) {
        vx_resc_t * vr = malloc(sizeof(vx_resc_t));

        // primitive fields
        vr->type = types_env[i];
        vr->count = counts_env[i];
        vr->fieldwidth = fieldwidths_env[i];
        vr->id = ids_env[i];
        vr->res = malloc(vr->count * vr->fieldwidth);


        // Copy over the resource XXX Do we need to case the array access?
        jobject jres  = (*jenv)->GetObjectArrayElement(jenv, jrescs, i);
        jbyte* res_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jres, NULL);
        memcpy(vr->res, res_env, vr->count * vr->fieldwidth);
        (*jenv)->ReleasePrimitiveArrayCritical(jenv, jres, res_env, 0);


        resources[i] = vr;
    }
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jtypes, types_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcounts, counts_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jfieldwidths, fieldwidths_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jids, ids_env, 0);

    vx_update_resources(nresc, resources);
    vx_update_buffer(buf_name, codes);

    return 0;
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_render
  (JNIEnv * jenv, jclass jcls)
{
    vx_render();
    return 0;
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_read_1pixels
  (JNIEnv * jenv, jclass jcls, jint width, jint height, jbyteArray jimg)
{

    jbyte* img_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jimg, NULL);
    int res = vx_read_pixels_bgr(width,height, (uint8_t *) img_env);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jimg, img_env, 0);

    return res;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_fbo_1create
  (JNIEnv * jenv, jclass jcls, jint width, jint height)
{
    return fbo_create(width, height);
}
