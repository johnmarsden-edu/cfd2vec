import os
import sys
import re

if __name__ =="__main__":
  sub_id = re.compile('#code_id#[^#]*#code_id#')
  f = open(sys.argv[1], 'r')
  for line in f:
    print(sub_id.sub('', line), end='')
  f.close()
