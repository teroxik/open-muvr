library(moments)
library(entropy)
library(foreach)
library(plyr)

########################################################################################################################
#
# Data enrichment
#
########################################################################################################################

log = FALSE
parallelCalculation = TRUE

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
  f(force(x))
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
    count = nrow(data)
    data[windowSize:count, colName] =
      ldply(1:(count - windowSize + 1), function(i) { f(data[i:(i + windowSize - 1),]) }, .parallel = parallelCalculation)
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
    sqrt( (as.double(data[[colX]]) ^ 2) + (as.double(data[[colY]]) ^ 2) + (as.double(data[[colZ]]) ^ 2) )
  }
}

# Calculates a filtered data view using the SVM compared against a threshold value.
#
# @param column    the column name
# @param threshold (mG) threshold value: above and we keep the column data; below and we zero the column data
# @param SVMColumn name of the data column holding the SVM calculated result
signalVectorMagnitudeFilter = function(column, threshold = 2000, SVMColumn = "SVM") {
  function(data) {
    as.double(data[[column]])*(as.double(data[[SVMColumn]]) >= threshold)
  }
}

# Calculates moving average by column.
#
# @param column     the column name
# @param data       the input data subset
movingAverageByColumn = function(column) {
  function(data) {
    mean(as.double(data[[column]]))
  }
}

# Calculates moving average.
#
# @param data       the input data subset
movingAverage = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    mean(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
  }
}

# Calculates simple standard deviation by column.
simpleStandardDeviationByColumn = function(column) {
  function(data) {
    sd(as.double(data[[column]]))
  }
}

# Calculates simple standard deviation.
#
# @param data       the input data subset
simpleStandardDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    sd(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
  }
}

# Calculates population standard deviation by column.
populationStandardDeviationByColumn = function(column) {
  function(data) {
    count = nrow(data)
    sd(as.double(data[[column]])) * sqrt((count - 1) / count)
  }
}

# Calculates population standard deviation.
#
# @param data       the input data subset
populationStandardDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    count = nrow(data) * 3
    sd(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]]))) * sqrt((count - 1) / count)
  }
}

# Calculates interquartile range by column.
interquartileRangeByColumn = function(column, type = 1) {
  function(data) {
    IQR(as.double(data[[column]]), type = type)
  }
}

# Calculates interquartile range.
interquartileRange = function(colX = "x", colY = "y", colZ = "z", type = 1) {
  function(data) {
    IQR(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])), type = type)
  }
}

# Calculates mean absolute deviation by column.
meanAbsoluteDeviationByColumn = function(column) {
  function(data) {
    dataMean = mean(as.double(data[[column]]))
    mean(abs(as.double(data[[column]]) - dataMean))
  }
}

# Calculates mean absolute deviation.
meanAbsoluteDeviation = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    dataMean = mean(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
    mean(abs(c(as.double(data[[colX]]) - dataMean, as.double(data[[colY]]) - dataMean, as.double(data[[colY]]) - dataMean)))
  }
}

# Calculates skewness by column.
skewnessByColumn = function(column) {
  function(data) {
    skewness(as.double(data[[column]]))
  }
}

# Calculates skewness.
overallSkewness = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    skewness(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
  }
}

# Calculates kurtosis by column.
kurtosisByColumn = function(column) {
  function(data) {
    kurtosis(as.double(data[[column]]))
  }
}

# Calculates kurtosis.
overallKurtosis = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    kurtosis(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
  }
}

# Calculates Q``quartile`` by column.
#
# @param quartile   the quartile numer (1, 2, 3)
quartileByColumn = function(quartile, column) {
  function(data) {
    quantile(as.double(data[[column]]))[[quartile + 1]]
  }
}

# Calculates quartile.
#
# @param quartile   the quartile numer (1, 2, 3)
overallQuartile = function(quartile, colX = "x", colY = "y", colZ = "z") {
  function(data) {
    quantile(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))[[quartile + 1]]
  }
}

# Calculates signal vector area by column.
signalVectorAreaByColumn = function(column) {
  function(data) {
    sum(abs(as.double(data[[column]])))
  }
}

# Calculates signal vector area.
signalVectorArea = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    sum(abs(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]]))))
  }
}

# Calculates empirical entropy by column.
entropyByColumn = function(column) {
  function(data) {
    entropy.empirical(as.double(data[[column]]))
  }
}

# Calculates empirical entropy.
overallEntropy = function(colX = "x", colY = "y", colZ = "z") {
  function(data) {
    entropy.empirical(c(as.double(data[[colX]]), as.double(data[[colY]]), as.double(data[[colZ]])))
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

  enrichData(windowSize, "ssdX", simpleStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "ssdY", simpleStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "ssdZ", simpleStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "ssd", simpleStandardDeviation()) %|>%

  enrichData(windowSize, "psdX", populationStandardDeviationByColumn("x")) %|>%
  enrichData(windowSize, "psdY", populationStandardDeviationByColumn("y")) %|>%
  enrichData(windowSize, "psdZ", populationStandardDeviationByColumn("z")) %|>%
  enrichData(windowSize, "psd", populationStandardDeviation()) %|>%

  enrichData(windowSize, "iqrX", interquartileRangeByColumn("x")) %|>%
  enrichData(windowSize, "iqrY", interquartileRangeByColumn("y")) %|>%
  enrichData(windowSize, "iqrZ", interquartileRangeByColumn("z")) %|>%
  enrichData(windowSize, "iqr", interquartileRange()) %|>%

  enrichData(windowSize, "madX", meanAbsoluteDeviationByColumn("x")) %|>%
  enrichData(windowSize, "madY", meanAbsoluteDeviationByColumn("y")) %|>%
  enrichData(windowSize, "madZ", meanAbsoluteDeviationByColumn("z")) %|>%
  enrichData(windowSize, "mad", meanAbsoluteDeviation()) %|>%

  enrichData(windowSize, "skewX", skewnessByColumn("x")) %|>%
  enrichData(windowSize, "skewY", skewnessByColumn("y")) %|>%
  enrichData(windowSize, "skewZ", skewnessByColumn("z")) %|>%
  enrichData(windowSize, "skew", overallSkewness()) %|>%

  enrichData(windowSize, "kurtX", kurtosisByColumn("x")) %|>%
  enrichData(windowSize, "kurtY", kurtosisByColumn("y")) %|>%
  enrichData(windowSize, "kurtZ", kurtosisByColumn("z")) %|>%
  enrichData(windowSize, "kurt", overallKurtosis()) %|>%

  enrichData(windowSize, "q1X", quartileByColumn(1, "x")) %|>%
  enrichData(windowSize, "q1Y", quartileByColumn(1, "y")) %|>%
  enrichData(windowSize, "q1Z", quartileByColumn(1, "z")) %|>%
  enrichData(windowSize, "q1", overallQuartile(1)) %|>%

  enrichData(windowSize, "q2X", quartileByColumn(2, "x")) %|>%
  enrichData(windowSize, "q2Y", quartileByColumn(2, "y")) %|>%
  enrichData(windowSize, "q2Z", quartileByColumn(2, "z")) %|>%
  enrichData(windowSize, "q2", overallQuartile(2)) %|>%

  enrichData(windowSize, "q3X", quartileByColumn(3, "x")) %|>%
  enrichData(windowSize, "q3Y", quartileByColumn(3, "y")) %|>%
  enrichData(windowSize, "q3Z", quartileByColumn(3, "z")) %|>%
  enrichData(windowSize, "q3", overallQuartile(3)) %|>%

  enrichData(windowSize, "svaX", signalVectorAreaByColumn("x")) %|>%
  enrichData(windowSize, "svaY", signalVectorAreaByColumn("y")) %|>%
  enrichData(windowSize, "svaZ", signalVectorAreaByColumn("z")) %|>%
  enrichData(windowSize, "sva", signalVectorArea()) %|>%

  enrichData(windowSize, "entX", entropyByColumn("x")) %|>%
  enrichData(windowSize, "entY", entropyByColumn("y")) %|>%
  enrichData(windowSize, "entZ", entropyByColumn("z")) %|>%
  enrichData(windowSize, "ent", overallEntropy()) %|>%

  # TODO: add more feature calculations here
  saveDataToCsv(outputFile)
  TRUE
}
