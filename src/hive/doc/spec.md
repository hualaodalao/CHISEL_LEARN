Hive为Gemm矩阵计算单元。

整体计算循环为MKN
A矩阵为正常左矩阵，计算循环为MKN取tile，为column-major
B矩阵当loadmode为0时，B矩阵为正常MAT，tile为column-major。当loadmode为1时，B矩阵应当为转置矩阵。当为mxfp格式时，必须loadmode为1，即B为转置矩阵，B按照K维进行mx缩放，同时tile为row major。