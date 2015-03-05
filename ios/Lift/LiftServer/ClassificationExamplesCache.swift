import Foundation

///
/// Caches classification examples by Exercise for
/// offline functionality
///
public class ClassificationExamplesCache {
    
    ///
    /// Singleton instance of the ClassificationExamplesCache. The instances are stateless, so it is generally a
    /// good idea to take advantage of the singleton
    ///
    public class var sharedInstance: ClassificationExamplesCache {
        struct Singleton {
            static let instance = ClassificationExamplesCache()
        }
        
        return Singleton.instance
    }
    
    private let cacheFilePath = ClassificationExamplesCache.getCacheFilePath()
    
    private class func getCacheFilePath() -> String {
        let documentsPath = NSSearchPathForDirectoriesInDomains(.DocumentDirectory, .UserDomainMask, true).first as String
        return documentsPath.stringByAppendingPathComponent("classificationExamplesCache.json")
    }
    
    private func loadCacheFile() -> [String : [String]] {
        if let fileData = NSData(contentsOfFile: cacheFilePath) {
            let json = JSON(data: fileData, options: NSJSONReadingOptions.AllowFragments, error: nil)
            return json.dictionaryValue.flatMapValues { (value: JSON) -> [String]? in
                return value.array?.flatMap { $0.string }
            }
        } else {
            return [:]
        }
    }
    
    private func saveCacheFile(values: [String : [String]]) {
        var error: NSError?
        let os = NSOutputStream(toFileAtPath: cacheFilePath, append: false)!
        os.open()
        NSJSONSerialization.writeJSONObject(values, toStream: os, options: NSJSONWritingOptions.PrettyPrinted, error: &error)
        os.close()
    }
    
    private func joinDistinct(values: [[String]]) -> [String] {
        var result: [String] = []
        for array in values {
            for current in array {
                let alreadyExists = (result.exists { current == $0 })
                if !alreadyExists {
                    result += [current]
                }
            }
        }
        return result
    }
    
    ///
    /// Returns the cached examples for ``sessionProps.muscleGroupKeys``
    ///
    func getExamplesFor(sessionProps: Exercise.SessionProps) -> [Exercise.Exercise] {
        let cachedDictionary = loadCacheFile()
        let examples = sessionProps.muscleGroupKeys.flatMap { cachedDictionary[$0] }
        let distinctValues = joinDistinct(examples)
        return map(distinctValues) { Exercise.Exercise(name: $0, intensity: nil, metric: nil) }
    }
    
    ///
    /// Updates the cache with the provided examples
    ///
    func addExamplesToCache(sessionProps: Exercise.SessionProps, values: [Exercise.Exercise]) {
        if sessionProps.muscleGroupKeys.count != 1 {
            return
        }
        
        let key = sessionProps.muscleGroupKeys.first!
        let value = values.flatMap { $0.name }
        var cache = loadCacheFile()
        cache[key] = value
        
        saveCacheFile(cache)
    }
    
}
