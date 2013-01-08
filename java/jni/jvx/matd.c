#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include "assert.h"
#include "matd.h"

// a matd_t with rows=0 cols=0 is a SCALAR.

// to ease creating mati, matf, etc. in the future.
#define TYPE double

#define EL(m, row,col) (m)->data[((row)*(m)->ncols + (col))]

matd_t *matd_create(int rows, int cols)
{
    matd_t *m = calloc(1, sizeof(matd_t));
    m->nrows = rows;
    m->ncols = cols;
    m->data = calloc(m->nrows * m->ncols, sizeof(TYPE));

    return m;
}

matd_t *matd_create_scalar(double v)
{
    matd_t *m = calloc(1, sizeof(matd_t));
    m->nrows = 0;
    m->ncols = 0;
    m->data = calloc(1, sizeof(TYPE));
    m->data[0] = v;

    return m;
}

matd_t *matd_create_data(int rows, int cols, const TYPE *data)
{
    matd_t *m = matd_create(rows, cols);
    for (int i = 0; i < rows * cols; i++)
        m->data[i] = data[i];

    return m;
}

matd_t *matd_copy(const matd_t *m)
{
    matd_t *x = matd_create(m->nrows, m->ncols);
    memcpy(x->data, m->data, sizeof(double)*m->ncols*m->nrows);
    return x;
}

void matd_print(const matd_t *m, const char *fmt)
{
    if (m->nrows == 0) {
        printf(fmt, EL(m, 0, 0));
    } else {
        for (int i = 0; i < m->nrows; i++) {
            for (int j = 0; j < m->ncols; j++) {
                printf(fmt, EL(m, i, j));
            }
            printf("\n");
        }
    }
}

void matd_destroy(matd_t *m)
{
    free(m->data);
    free(m);
}

matd_t *matd_multiply(const matd_t *a, const matd_t *b)
{
    if (a->nrows == 0)
        return matd_scale(b, a->data[0]);
    if (b->nrows == 0)
        return matd_scale(a, b->data[0]);

    assert(a->ncols == b->nrows);
    matd_t *m = matd_create(a->nrows, b->ncols);

    for (int i = 0; i < m->nrows; i++) {
        for (int j = 0; j < m->ncols; j++) {
            TYPE acc = 0;
            for (int k = 0; k < a->ncols; k++) {
                acc += EL(a, i, k) * EL(b, k, j);
            }
            EL(m, i, j) = acc;
        }
    }

    return m;
}

matd_t *matd_scale(const matd_t *a, double s)
{
    if (a->nrows == 0)
        return matd_create_scalar(a->data[0] * s);

    matd_t *m = matd_create(a->nrows, a->ncols);

    for (int i = 0; i < m->nrows; i++) {
        for (int j = 0; j < m->ncols; j++) {
            EL(m, i, j) = s * EL(a, i, j);
        }
    }

    return m;
}

matd_t *matd_add(const matd_t *a, const matd_t *b)
{
    assert(a->nrows == b->nrows);
    assert(a->ncols == b->ncols);

    if (a->nrows == 0)
        return matd_create_scalar(a->data[0] + b->data[0]);

    matd_t *m = matd_create(a->nrows, a->ncols);

    for (int i = 0; i < m->nrows; i++) {
        for (int j = 0; j < m->ncols; j++) {
            EL(m, i, j) = EL(a, i, j) + EL(b, i, j);
        }
    }

    return m;
}

void matd_add_inplace(matd_t *a, const matd_t *b)
{
    assert(a->nrows == b->nrows);
    assert(a->ncols == b->ncols);

    if (a->nrows == 0) {
        a->data[0] += b->data[0];
        return;
    }

    for (int i = 0; i < a->nrows; i++) {
        for (int j = 0; j < a->ncols; j++) {
            EL(a, i, j) += EL(b, i, j);
        }
    }
}


matd_t *matd_subtract(const matd_t *a, const matd_t *b)
{
    assert(a->nrows == b->nrows);
    assert(a->ncols == b->ncols);

    if (a->nrows == 0)
        return matd_create_scalar(a->data[0] - b->data[0]);

    matd_t *m = matd_create(a->nrows, a->ncols);

    for (int i = 0; i < m->nrows; i++) {
        for (int j = 0; j < m->ncols; j++) {
            EL(m, i, j) = EL(a, i, j) - EL(b, i, j);
        }
    }

    return m;
}

matd_t *matd_transpose(const matd_t *a)
{
    if (a->nrows == 0)
        return matd_create_scalar(a->data[0]);

    matd_t *m = matd_create(a->ncols, a->nrows);

    for (int i = 0; i < a->nrows; i++) {
        for (int j = 0; j < a->ncols; j++) {
            EL(m, j, i) = EL(a, i, j);
        }
    }
    return m;
}

// XXX How to handle singular matrices?
matd_t *matd_inverse(const matd_t *x)
{
    assert(x->nrows == x->ncols);

    switch(x->nrows) {
    case 0:
        // scalar
        return matd_create_scalar(1.0 / x->data[0]);

    case 1: {
        // a 1x1 matrix
        matd_t *m = matd_create(x->nrows, x->nrows);
        EL(m, 0, 0) = 1.0 / x->data[0];
        return m;
    }

    case 2: {
        matd_t *m = matd_create(x->nrows, x->nrows);
        double invdet = 1.0 / (EL(x, 0, 0) * EL(x, 1, 1) - EL(x, 0, 1) * EL(x, 1, 0));
        EL(m, 0, 0) = EL(x, 1, 1) * invdet;
        EL(m, 0, 1) = - EL(x, 0, 1) * invdet;
        EL(m, 1, 0) = - EL(x, 1, 0) * invdet;
        EL(m, 1, 1) = EL(x, 0, 0) * invdet;
        return m;
    }

    case 3: {
        matd_t *m = matd_create(x->nrows, x->nrows);

        double a = EL(x, 0, 0), b = EL(x, 0, 1), c = EL(x, 0, 2);
        double d = EL(x, 1, 0), e = EL(x, 1, 1), f = EL(x, 1, 2);
        double g = EL(x, 2, 0), h = EL(x, 2, 1), i = EL(x, 2, 2);

        double det = (a*e*i-a*f*h-d*b*i+d*c*h+g*b*f-g*c*e);
        det = 1.0/det;

        EL(m,0,0) = det*(e*i-f*h);
        EL(m,0,1) = det*(-b*i+c*h);
        EL(m,0,2) = det*(b*f-c*e);
        EL(m,1,0) = det*(-d*i+f*g);
        EL(m,1,1) = det*(a*i-c*g);
        EL(m,1,2) = det*(-a*f+c*d);
        EL(m,2,0) = det*(d*h-e*g);
        EL(m,2,1) = det*(-a*h+b*g);
        EL(m,2,2) = det*(a*e-b*d);
        return m;

    }
    }

    printf("Unimplemented matrix inverse of size %d\n", x->nrows);
    assert(0);
}

// TODO Optimization: Some operations we could perform in-place,
// saving some memory allocation work. E.g., ADD, SUBTRACT. Just need to make sure
// that we don't do an in-place modification on a matrix that was an input argument.

// handle right-associative operators, greedily consuming them. These
// include transpose and inverse. This is called by the main recursion
// method.
static inline matd_t *matd_op_gobble_right(const char *expr, int *pos, matd_t *acc, matd_t **garb, int *garbpos, int *isarg)
{
    while (expr[*pos] != 0) {

        switch (expr[*pos]) {

        case '\'': {
            assert(acc != NULL);
            matd_t *res = matd_transpose(acc);
            garb[*garbpos] = res;
            (*garbpos)++;
            acc = res;
            *isarg = 0;

            (*pos)++;
            break;
        }

            // handle inverse ^-1. No other exponents are allowed.
        case '^': {
            assert(acc != NULL);
            assert(expr[*pos+1] == '-');
            assert(expr[*pos+2] == '1');

            matd_t *res = matd_inverse(acc);
            garb[*garbpos] = res;
            (*garbpos)++;
            acc = res;
            *isarg = 0;

            (*pos)+=3;
            break;
        }

        default:
            return acc;
        }
    }

    return acc;
}

// @garb, garbpos  A list of every matrix allocated during evaluation... used to assist cleanup.
// @isarg: The returned value is one of the original arguments. Set to 0 if we're returning a newly-allocated.
// @oneterm: we should return at the end of this term (i.e., stop at a PLUS, MINUS, LPAREN).
static matd_t *matd_op_recurse(const char *expr, int *pos, matd_t *acc, matd_t **args, int *argpos,
                               matd_t **garb, int *garbpos, int *isarg, int oneterm)
{
    while (expr[*pos] != 0) {

        switch (expr[*pos]) {

        case '(': {
            if (oneterm && acc != NULL)
                return acc;
            (*pos)++;
            matd_t *rhs = matd_op_recurse(expr, pos, NULL, args, argpos, garb, garbpos, isarg, 0);
            rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

            if (acc == NULL) {
                acc = rhs;
                // isarg is unchanged--- whatever the recursive call did above.
            } else {
                matd_t *res = matd_multiply(acc, rhs);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
                *isarg = 0;
            }

            break;
        }

        case ')': {
            if (oneterm)
                return acc;

            (*pos)++;
            return acc;
        }

        case '*':
            (*pos)++;

            matd_t *rhs = matd_op_recurse(expr, pos, NULL, args, argpos, garb, garbpos, isarg, 1);
            rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

            if (acc == NULL) {
                acc = rhs;
            } else {
                matd_t *res = matd_multiply(acc, rhs);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
                *isarg = 0;
            }

            break;


        case 'M': {
            matd_t *rhs = args[*argpos];
            (*pos)++;
            (*argpos)++;

            rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

            if (acc == NULL) {
                acc = rhs;
                *isarg = 1;
            } else {
                matd_t *res = matd_multiply(acc, rhs);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
                *isarg = 0;
            }

            break;
        }

/*
        case 'D': {
            int rows = expr[*pos+1]-'0';
            int cols = expr[*pos+2]-'0';

            matd_t *rhs = matd_create(rows, cols);

            break;
        }
*/
            // a constant (SCALAR) defined inline. Treat just like M, creating a matd_t on the fly.
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
        case '.': {
            const char *start = &expr[*pos];
            char *end;
            double s = strtod(start, &end);
            (*pos) += (end - start);
            matd_t *rhs = matd_create_scalar(s);
            garb[*garbpos] = rhs;
            (*garbpos)++;

            rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

            if (acc == NULL) {
                acc = rhs;
            } else {
                matd_t *res = matd_multiply(acc, rhs);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
            }

            *isarg = 0;
            break;
        }

        case '+': {
            if (oneterm && acc != NULL)
                return acc;

            // don't support unary plus
            assert(acc != NULL);
            (*pos)++;
            matd_t *rhs = matd_op_recurse(expr, pos, NULL, args, argpos, garb, garbpos, isarg, 1);
            rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

            // This is an example of an in-place optimization.
            // original code:
            // matd_t *res = matd_add(acc, rhs);
            matd_t *res;
            if (!isarg) {
                matd_add_inplace(rhs, acc);
                res = rhs;
            } else {
                res = matd_add(acc, rhs);
            }

            garb[*garbpos] = res;
            (*garbpos)++;
            acc = res;
            *isarg = 0;
            break;
        }

        case '-': {
            if (oneterm && acc != NULL)
                return acc;

            if (acc == NULL) {
                // unary minus
                (*pos)++;
                matd_t *rhs = matd_op_recurse(expr, pos, NULL, args, argpos, garb, garbpos, isarg, 1);
                rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

                matd_t *res = matd_scale(rhs, -1);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
                *isarg = 0;
            } else {
                // subtract
                (*pos)++;
                matd_t *rhs = matd_op_recurse(expr, pos, NULL, args, argpos, garb, garbpos, isarg, 1);
                rhs = matd_op_gobble_right(expr, pos, rhs, garb, garbpos, isarg);

                matd_t *res = matd_subtract(acc, rhs);
                garb[*garbpos] = res;
                (*garbpos)++;
                acc = res;
                *isarg = 0;
            }
            break;
        }

        case ' ':
            // nothing to do. spaces are meaningless.
            (*pos)++;
            break;

        default:
            (*pos)++;
            printf("Unknown character '%c'\n", expr[*pos]);
            break;
        }
    }
    return acc;
}

// always returns a new matrix.
matd_t *matd_op(const char *expr, ...)
{
    int nargs = 0;
    int exprlen = 0;
    for (const char *p = expr; *p != 0; p++) {
        if (*p == 'M')
            nargs++;
        exprlen++;
    }

    va_list ap;
    va_start(ap, expr);

    matd_t *args[nargs];
    for (int i = 0; i < nargs; i++) {
        args[i] = va_arg(ap, matd_t*);
        // XXX: sanity check argument; emit warning/error if args[i]
        // doesn't look like a matd_t*.
    }

    va_end(ap);

    int pos = 0;
    int argpos = 0;
    int garbpos = 0;
    int isarg = 0;

    matd_t *garb[exprlen]; // can't create more than 1 new result per character

    matd_t *res = matd_op_recurse(expr, &pos, NULL, args, &argpos, garb, &garbpos, &isarg, 0);

    for (int i = 0; i < garbpos; i++) {
        if (garb[i] != res)
            matd_destroy(garb[i]);
    }

    if (isarg)
        res = matd_copy(res);

    return res;
}

// So far, no reason to distinguish between row vectors and column vectors
static inline int is_vector(const matd_t * a)
{
    return a->ncols == 1 || a->nrows == 1;
}

static inline int is_vector_len(const matd_t * a, int len)
{
    return (a->ncols == 1 && a->nrows == len) || (a->ncols == len && a->nrows == 1);
}

static inline double sq(double v)
{
    return v*v;
}


double matd_vec_mag(const matd_t *a)
{
    assert(is_vector(a));

    double mag = 0.0;
    int len = a->nrows*a->ncols;
    for (int i = 0; i < len; i++)
        mag += sq(a->data[i]);
    return mag;
}

matd_t *matd_vec_normalize(const matd_t *a)
{
    double mag = matd_vec_mag(a);

    matd_t *b = matd_create(a->nrows, a->ncols);

    int len = a->nrows*a->ncols;
    for(int i = 0; i < len; i++)
        b->data[i] = a->data[i] / mag;

    return b;
}


matd_t *matd_crossproduct(const matd_t *a, const matd_t *b)
{ // only defined for vecs (col or row) of length 3
    assert(is_vector_len(a, 3) && is_vector_len(b, 3));

    matd_t * r = matd_create(a->nrows, a->ncols);

    r->data[0] = a->data[1] * b->data[2] - a->data[2] * b->data[1];
    r->data[1] = a->data[2] * b->data[0] - a->data[0] * b->data[2];
    r->data[2] = a->data[0] * b->data[1] - a->data[1] * b->data[0];

    return r;
}
