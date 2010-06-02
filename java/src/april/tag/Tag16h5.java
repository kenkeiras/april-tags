package april.tag;

/** Tag family with 30 distinct codes.
    bits: 16,  minimum hamming: 5,  minimum complexity: 5

    Max bits corrected       False positive rate
            0                    0.045776 %
            1                    0.778198 %
            2                    6.271362 %

    Generation time: 0.313000 s

    Hamming distance between pairs of codes (accounting for rotation):

       0  0
       1  0
       2  0
       3  0
       4  0
       5  106
       6  150
       7  92
       8  52
       9  23
      10  8
      11  2
      12  1
      13  1
      14  0
      15  0
      16  0
**/
public class Tag16h5 extends TagFamily
{
	public Tag16h5()
	{
		super(16, 5, new long[] { 0x231bL, 0x2ea5L, 0x346aL, 0x45b9L, 0x6857L, 0x7f6bL, 0xad93L, 0xb358L, 0xb91dL, 0xe745L, 0x156dL, 0xd3d2L, 0xdf5cL, 0x4736L, 0x8c72L, 0x5a02L, 0xd32bL, 0x1867L, 0x468fL, 0xdc91L, 0x4940L, 0xa9edL, 0x2bd5L, 0x599aL, 0x9009L, 0x61f6L, 0x3850L, 0x8157L, 0xbfcaL, 0x987cL });
	}
}
