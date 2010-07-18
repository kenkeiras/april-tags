#include "scip2.h"

#include <pthread.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "common/varray.h"
#include "common/vhash.h"
#include "common/ioutils.h"
#include "common/timespec.h"

#define MAX_LINE_LENGTH 128

#define STATUS_WAITING 0
#define STATUS_DONE 1
#define STATUS_CHECKSUM_ERR 2

typedef struct scip2_transaction scip2_transaction_t;
struct scip2_transaction
{
    uint32_t xid;

    varray_t *response;
    int status;

    // used to wake up the right caller when their response arrives.
    pthread_mutex_t mutex;
    pthread_cond_t cond;

    // used for callbacks
    int (*callback)(varray_t *response, void *user);
    void *user;

};

static void handle_response(scip2_t *scip, varray_t *response)
{
    pthread_mutex_lock(&scip->mutex);

    if (varray_size(response) == 0)
        goto error;

    char *line0 = varray_get(response, 0);
    char *xids = index(line0, ';');
    if (xids == NULL) {
        printf("scip handle-response: no transaction id %s\n", line0);
        goto error;
    }

    uint32_t xid = strtoll(&xids[1], NULL, 16);
    scip2_transaction_t *trans = vhash_get(scip->transactions, xid);
    if (trans == NULL) {
        printf("scip handle-response: unknown transaction %s\n", line0);
        goto error;
    }

    // check the checksums (on every line except the first line, which
    // doesn't have one.)
    for (int i = 1; i+1 < varray_size(response); i++) {
        char *line = varray_get(response, i);

        int len = strlen(line);
        int chklen = len - 2; // skip newline and sent checksum byte

        // For commands like 'PP', the semicolon does not appear to be
        // included in the checksum. This is a bug in the
        // documentation?
        if (line[chklen-1] == ';')
            chklen--;

        uint8_t chk = 0;
        for (int j = 0; j < chklen; j++)
            chk += line[j];

        int sent_chk = line[len-2];

        chk = (chk&0x3f) + 0x30;

        if (chk != sent_chk) {
            trans->status = STATUS_CHECKSUM_ERR;
            scip2_response_free(scip, response);
            response = NULL;
            printf("bad checksum %c != %c\n", chk, sent_chk);
            goto error;
        }
    }

    pthread_mutex_unlock(&scip->mutex);

    // Let the creator of the transaction know that their result is ready.
    pthread_mutex_lock(&trans->mutex);

    if (trans->callback != NULL)
        trans->callback(response, trans->user);

    trans->response = response;
    trans->status = STATUS_DONE;
    pthread_cond_broadcast(&trans->cond);
    pthread_mutex_unlock(&trans->mutex);

    return;

error:
    printf("scip handle_response error\n");
    pthread_mutex_unlock(&scip->mutex);

    // nobody else is going to use this response!
    scip2_response_free(scip, response);
}

static void *reader_thread(void *_a)
{
    scip2_t *scip = (scip2_t*) _a;

    // Packets from the URG are sequences of ASCII lines, each
    // terminated by a LF. The packet ends with an extra LF.
    while (1) {
        varray_t *response = varray_create();

        while (1) {
            char *line = malloc(MAX_LINE_LENGTH);
            if (fgets(line, MAX_LINE_LENGTH, scip->f) == NULL)
                exit(1);

            if (scip->debug)
                printf("READ : %s", line);

            varray_add(response, line);

            // last line of the response?
            if ((line[0]=='\n') && line[1] == '\0')
                break;
        }
        handle_response(scip, response);

        // "response" is deallocated once the response has been handled
        // (see scip2_response_free, which is called by the user.)
    }

    return NULL;
}

void scip2_response_free(scip2_t *scip, varray_t *response)
{
    // Free the memory.
    for (int i = 0; i < varray_size(response); i++) {
        free(varray_get(response, i));
    }
    varray_destroy(response);
}

// A SCIP request is a line of ASCII, usually terminated by a LF. In
// this case, the line feed should be omitted when passed in: we will
// append a transaction ID so that we can return the appropriate
// response.  The caller should call scip2_response_free() on the
// result.  The first response (which is usually an acknowledgement)
// is returned. Subsequent asynchronous responses are passed to the
// callback.
//
// @param command   The command (without newlines or identification strings.) E.g. "RS"
//
// @param callback  If non-null, any future messages bearing the
//                  identification code will be sent to the callback
//                  function. The callback should free the
//                  response. If the callback returns non-zero, future
//                  callbacks will be ignored.
//
// @param user      A context variable passed to callback. May be NULL.
//
// @param timeoutms Maximum delay before receiving confirmation. Use 0 for no timeout.
//
// @returns         The response to the original command, which the user
//                  should deallocate with
//                  scip2_response_free. Returns NULL on timeout.
//
varray_t *scip2_transaction(scip2_t *scip, const char *command, int(*callback)(varray_t *response, void *user), void *user, int timeoutms)
{
    scip2_transaction_t *trans = (scip2_transaction_t*) calloc(1, sizeof(scip2_transaction_t));
    pthread_mutex_init(&trans->mutex, NULL);
    pthread_cond_init(&trans->cond, NULL);
    trans->response = NULL;

    ////////////////////////////////////////////////
    // prepare the transaction
    pthread_mutex_lock(&scip->mutex);

    trans->xid = scip->xid;
    scip->xid++;

    trans->user = user;
    trans->callback = callback;

    ////////////////////////////////////////////////
    // write the request, appending the transaction id.
    char fullcmd[1024];
    int len = snprintf(fullcmd, 1024, "%s;%08x\n", command, trans->xid);

    vhash_put(scip->transactions, (void*) trans->xid, trans);

    write_fully(scip->fd, fullcmd, len);

    if (scip->debug)
        printf("WRITE: %s\n", fullcmd);

    pthread_mutex_unlock(&scip->mutex);

    ////////////////////////////////////////////////
    // wait for the response
    pthread_mutex_lock(&trans->mutex);
    if (trans->status == STATUS_WAITING) {
        if (timeoutms > 0) {
            struct timespec ts;
            timespec_set(&ts, timeoutms / 1000.0);
            pthread_cond_timedwait(&trans->cond, &trans->mutex, &ts);
        } else {
            pthread_cond_wait(&trans->cond, &trans->mutex);
        }
    }
    pthread_mutex_unlock(&trans->mutex);

    varray_t *response = trans->response;

    ////////////////////////////////////////////////
    // free the transaction unless A) This command succeeded AND B) a
    // callback has been registered.
    if (callback == NULL || response == NULL) {
        pthread_mutex_lock(&scip->mutex);

        vhash_remove(scip->transactions, (void*) trans->xid);

        pthread_cond_destroy(&trans->cond);
        pthread_mutex_destroy(&trans->mutex);
        free(trans);

        pthread_mutex_unlock(&scip->mutex);
    }

    return response;
}

static int sync_callback(varray_t *response, void *user)
{
    return 0;
}

varray_t *scip2_transaction_sync(scip2_t *scip, const char *command, int timeoutms)
{
    scip2_transaction(scip, command, sync_callback, NULL, timeoutms);
}

scip2_t *scip2_create(const char *path)
{
    scip2_t *scip = (scip2_t*) calloc(1, sizeof(scip2_t));

    scip->fd = open(path, O_RDWR | O_NOCTTY, 0);
    if (scip->fd < 0)
        return NULL;

    scip->transactions = vhash_create(vhash_uint32_hash, vhash_uint32_equals);
    srandom(time(NULL));
    scip->xid = random();

    scip->f = fdopen(scip->fd, "r");
    if (scip->f == NULL)
        return NULL;

    pthread_mutex_init(&scip->mutex, NULL);
    pthread_create(&scip->reader_thread, NULL, reader_thread, scip);

    return scip;
}


