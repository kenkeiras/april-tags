
vx_obj_opcodes_t * vx_obj_opcodes_create(int ncodes, int nresc)
{

    vx_obj_opcodes_t * v = malloc(sizeof(vx_obj_opcodes_t));
    v.ncodes = ncodes;
    v.codes = malloc(sizeof(uint32_t) * v.ncodes);

    v.nresc = nresc;
    v.rescs = malloc(sizeof(vx_resc_t)*v.nresc);

    return v;
}
