#ifndef _MATD_H
#define _MATD_H

typedef struct
{
    int nrows, ncols;

    // data in row-major order (index = rows*ncols + col)
    double *data;
} matd_t;


matd_t *matd_create(int rows, int cols);
matd_t *matd_create_data(int rows, int cols, const double *data);

matd_t *matd_copy(const matd_t *m);

// fmt is printf specifier for each individual element. Each row will
// be printed on a separate newline.
void matd_print(const matd_t *m, const char *fmt);

matd_t *matd_add(const matd_t *a, const matd_t *b);
matd_t *matd_subtract(const matd_t *a, const matd_t *b);
matd_t *matd_scale(const matd_t *a, double s);

matd_t *matd_multiply(const matd_t *a, const matd_t *b);
matd_t *matd_transpose(const matd_t *a);
matd_t *matd_inverse(const matd_t *a);


double matd_vec_mag(const matd_t *a);
matd_t *matd_vec_normalize(const matd_t *a);
matd_t *matd_crossproduct(const matd_t *a, const matd_t *b); // only defined for vecs (col or row) of length 3

matd_t *matd_op(const char *expr, ...);

void matd_destroy(matd_t *m);

#endif
