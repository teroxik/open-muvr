
########################################################################################################################
#
# Data enrichment
#
########################################################################################################################

# Reads data frame from the provided csv file.
#
# @param input  path of the input file
readDataCsv = function(input) {
  read.csv(file = input, header = TRUE)
}


