#include "april_vx_VxLocalServer.h"

#include "vx.h"
#include "vx_obj_opcodes.h"
#include "string.h"

JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_gl_1initialize
  (JNIEnv *jenv, jclass jcls)
{
    return vx_init();
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
        vr->fieldwidth = fieldwidths_env[i];
        vr->id = ids_env[i];
        vr->res = malloc(vr->count * vr->fieldwidth);


        // Copy over the resource XXX Do we need to case the array access?
        jobject jres  = (*jenv)->GetObjectArrayElement(jenv, jrescs, i);
        jbyte* res_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jres, NULL);
        memcpy(vr->res, res_env, vr->count * vr->fieldwidth);
        (*jenv)->ReleasePrimitiveArrayCritical(jenv, jres, res_env, 0);

        // Copy over the name XXXX This isn't the correct design
        jobject jname  = (*jenv)->GetObjectArrayElement(jenv, jnames, i);
        jbyte* name_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jname, NULL);
        vr->name = strdup(name_env);
        (*jenv)->ReleasePrimitiveArrayCritical(jenv, jname, name_env, 0);
    }
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jtypes, types_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcounts, counts_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jfieldwidths, fieldwidths_env, 0);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jids, ids_env, 0);





    return 0;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalServer_fbo_1create
  (JNIEnv * jenv, jclass jcls, jint width, jint height)
{
    return fbo_create(width, height);
}
