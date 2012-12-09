#include "april_vx_VxLocalRenderer.h"
#include <malloc.h>

#include "vx.h"
#include "vx_code_input_stream.h"
#include <string.h>

#include "vhash.h"



static uint32_t instanceIDcounter;
static vhash_t * vx_instance_map;

static void init_vx_jni()
{
    instanceIDcounter = 1;
    vx_instance_map = vhash_create(vhash_uint32_hash, vhash_uint32_equals);
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_init
(JNIEnv * jenv, jclass jcls)
{
    init_vx_jni();

    return vx_initialize();
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_create
(JNIEnv * jenv, jclass jcls)
{
    uint32_t instanceID = instanceIDcounter++;



    //XXX instance IDs
    vx_create();
    return instanceID;
}

/*
 * Class:     april_vx_VxLocalRenderer
 * Method:    destroy
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_destroy
(JNIEnv * jenv, jclass jcls, jint instanceID)
{
    return 0;
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_add_1resources
(JNIEnv * jenv, jclass jcls, jint instanceID,
 jint nresc, jintArray jtypes, jobjectArray jrescs, jintArray jcounts, jintArray jfieldwidths, jlongArray jids)
{

    // XXX Ignoring instanceID right now

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

    return 0;
}


JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_update_1buffer
(JNIEnv * jenv, jclass jcls, jint instanceID, jinti worldID, jbyteArray jbuf_name, jint draw_order, jint codes_len, jbyteArray jcodes)
{

    // Grab buffer name
    jbyte *buf_name_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jbuf_name, NULL);
    char * buf_name = strdup((char*)buf_name_env);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuf_name, buf_name, 0);

    // Copy over the Opcodes and integer parameters
    jbyte * codes_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jcodes, NULL);
    vx_code_input_stream_t * codes = vx_code_input_stream_init((uint8_t *)codes_env, (uint32_t)codes_len);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jcodes, codes_env, 0);


    vx_update_codes(buf_name, draw_order, codes);

    return 0;
}

JNIEXPORT void JNICALL Java_april_vx_VxLocalRenderer_deallocate_1resources
(JNIEnv * jenv, jclass jcls, jint instanceID, jlongArray jguids, jint nguids)
{

    jlong* guids = (*jenv)->GetPrimitiveArrayCritical(jenv, jguids, NULL);
    vx_deallocate_resources((uint64_t *)guids, nguids);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jguids, guids, 0);
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_render
  (JNIEnv * jenv, jclass jcls, jint instanceID,  jint width, jint height, jbyteArray jimg)
{
    jbyte* img_env = (*jenv)->GetPrimitiveArrayCritical(jenv, jimg, NULL);
    int res = vx_render_read(width, height, (uint8_t *) img_env);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jimg, img_env, 0);

    return res;
}

JNIEXPORT jint JNICALL Java_april_vx_VxLocalRenderer_set_1system_1pm_1matrix
(JNIEnv * jenv, jclass jcls, jint instanceID, jfloatArray jpm_mat)
{

    jfloat* pm_mat = (*jenv)->GetPrimitiveArrayCritical(jenv, jpm_mat, NULL);
    int ret = vx_set_system_pm_mat(pm_mat);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jpm_mat, pm_mat, 0);

    return ret;
}

