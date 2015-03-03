
########################################################################################################################
#
# Data enrichment
#
########################################################################################################################

log = TRUE

# Writes a log message if logging is enabled.
#
# @param message    the message
writeLogMessage = function(message) {
  if (log) {
   write(message, stdout())
   flush(stdout())
  }
}

# String concatenation operator
"%+%" = paste0

# Forward composition operator
# x %|>% f = f(x)
#
# @param x  the value to apply to ``f``
# @param f  the function to evaluate with ``x``
"%|>%" = function (x, f) {
  f(x)
}

# Reads data frame from the provided csv file.
#
# @param input  path of the input file
readDataCsv = function(input) {
  writeLogMessage("Reading data from input file...")
  read.csv(file = input, header = TRUE)
}

# Enriches the data frame with function ``f``, called with
# ``windowSize`` rows.
#
# @param windowSize the size of the sliding window
# @param colName    the name of the column where the data will be stored
# @param f          the function that calculates the enriched values
# @param data       the input data
enrichData = function(windowSize, colName, f) {
  force(f)
  function(data) {
    writeLogMessage("Calculating " %+% colName %+% " ...")
    for (i in windowSize:nrow(data)) {
      data[i, colName] = f(data[(i - windowSize + 1):i,])
    }
    data
  }
}

# Saves data frame to csv file ``output``.
#
# @param output     the path of the output file
# @param data       the data frame to output
saveDataToCsv = function(output) {
  function(data) {
    writeLogMessage("Saving data to output file...")
    write.table(data, file = output, row.names = FALSE, sep = ",")
  }
}

# Calculates the signal vector magnitude of the
# samples. Calculation is done on a sample basis
# (ignores sliding window).
#
# @param data       the input data subset
signalVectorMagnitude = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    sqrt( (data[[colX]] ^ 2) + (data[[colY]] ^ 2) + (data[[colZ]] ^ 2) )
  }
}

# Calculates moving average by column.
#
# @param column     the column name
# @param data       the input data subset
movingAverageByColumn = function(column) {
  function(data) {
    mean(data[[column]])
  }
}

# Calculates moving average.
#
# @param data       the input data subset
movingAverage = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    mean(c(data[[colX]], data[[colY]], data[[colZ]]))
  }
}

# Enriches data with all implemented features.
#
# @param inputFile      path of the input csv file
# @param outputFile     path of the output csv file
# @param windowSize     the size of the sliding window
enrichDataWithAllFeatures = function(inputFile, outputFile, windowSize) {
  inputFile %|>%
  readDataCsv %|>%
  enrichData(1, "SVM", signalVectorMagnitude()) %|>%
  enrichData(windowSize, "MeanX", movingAverageByColumn("x")) %|>%
  enrichData(windowSize, "MeanY", movingAverageByColumn("y")) %|>%
  enrichData(windowSize, "MeanZ", movingAverageByColumn("z")) %|>%
  enrichData(windowSize, "Mean", movingAverage()) %|>%
  # TODO: add more feature calculations here
  saveDataToCsv(outputFile)
  TRUE
}
