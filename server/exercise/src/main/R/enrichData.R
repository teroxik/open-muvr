
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

# Enriches the data frame with function ``f``, called with
# ``windowSize`` rows.
#
# @param data       the input data
# @param windowSize the size of the sliding window
# @param colName    the name of the column where the data will be stored
# @param f          the function that calculates the enriched values
enrichData = function(data, windowSize, colName, f) {
  for (i in windowSize:nrow(data)) {
    data[i, colName] = f(data[(i - windowSize + 1):i,])
  }
  data
}

# Saves data frame to csv file ``output``.
#
# @param output     the path of the output file
# @param data       the data frame to output
saveDataToCsv = function(output, data) {
  write.table(data, file = output, row.names = FALSE, sep = ",")
}
