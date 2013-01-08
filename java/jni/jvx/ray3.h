#ifndef RAY3_H
#define RAY3_H

typedef struct {
    double source[3];
    double dir[3];
} ray3_t;

void ray3_intersect_xy(ray3_t * ray, double zheight, double * vec3_out);

#endif
