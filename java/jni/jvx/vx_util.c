#include "vx_util.h"
#include <stdlib.h>
#include <sys/time.h>
#include "matd.h"
#include "varray.h"

uint64_t xxxAtomicLong = 1; //XX XX

uint64_t vx_alloc_id()
{
    return xxxAtomicLong++;
}


uint64_t vx_mtime()
{
    // get the current time
    struct timeval tv;
    gettimeofday (&tv, NULL);
    return (int64_t) tv.tv_sec * 1000 + tv.tv_usec/1000;
}


void vx_util_unproject(double * winxyz, double * model_matrix, double * projection_matrix, int * viewport, double * vec3_out)
{
    varray_t * fp = varray_create();

    matd_t * mm = varray_add(fp, matd_create_data(4, 4, model_matrix));
    matd_t * pm = varray_add(fp, matd_create_data(4, 4, projection_matrix));
    matd_t *invpm = varray_add(fp, matd_op("(MM)^-1", mm, pm));

    double v[4] = { 2*(winxyz[0]-viewport[0]) / viewport[2] - 1,
                    2*(winxyz[1]-viewport[1]) / viewport[3] - 1,
                    2*winxyz[2] - 1,
                    1 };
    matd_t * vm = varray_add(fp, matd_create_data(4, 1, v));
    matd_t * objxyzh = varray_add(fp,matd_op("MM", invpm, vm));
    vec3_out[0] = objxyzh->data[0] / objxyzh->data[3];
    vec3_out[1] = objxyzh->data[1] / objxyzh->data[3];
    vec3_out[2] = objxyzh->data[2] / objxyzh->data[3];

    // cleanup
    varray_map(fp, matd_destroy);
    varray_destroy(fp);
}
