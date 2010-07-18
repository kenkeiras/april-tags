#ifndef _SCIP2_H
#define _SCIP2_H

#include <stdio.h>
#include <pthread.h>
#include <stdint.h>
#include "common/varray.h"
#include "common/vhash.h"

typedef struct scip2 scip2_t;
struct scip2
{
    int fd;
    FILE *f;

    int debug;

    pthread_t reader_thread;
    uint32_t xid;

    vhash_t *transactions; // (void*) xid-> scip2_transaction_t

    pthread_mutex_t mutex; // protects writes on fd and xid, and transactions hash table.
};

scip2_t *scip2_create(const char *path);

varray_t *scip2_transaction(scip2_t *scip, const char *command, int(*callback)(varray_t *response, void *user), void *user, int timeoutms);

void scip2_response_free(scip2_t *scip, varray_t *response);

#endif
