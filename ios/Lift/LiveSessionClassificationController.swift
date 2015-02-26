import Foundation

///
/// Implement this protocol to receive notifications of tagging
/// on the LiveSessionTagView
///
protocol LiveSessionClassificationTagDelegate {
    
    func doneTagging(exercise: Exercise.Exercise, intensity: Exercise.ExerciseIntensityKey, repetition: Int)
    
}

class LiveSessionTagView : UIView {
    
    private let selectedButtonColor: UIColor = UIColor(red: 0.25098040700000002, green: 0.50196081400000003, blue: 0.0, alpha: 1)
    private let nonSelectedButtonColor: UIColor = UIColor(red: 0.0, green: 0.47843137250000001, blue: 1.0, alpha: 1)
    
    /// default values
    struct Defaults {
        static let repetitions: [Int] = [2, 5, 8, 10]
        static let intensities: [Exercise.ExerciseIntensityKey] = [
            Exercise.ExerciseIntensity.light,
            Exercise.ExerciseIntensity.moderate,
            Exercise.ExerciseIntensity.hard,
            Exercise.ExerciseIntensity.brutal
            ].map { $0.intensity }
    }
    
    private var delegate: LiveSessionClassificationTagDelegate?
    
    @IBOutlet var titleLabel: UILabel!
    
    @IBOutlet var defaultIntensityButton: UIButton!
    @IBOutlet var leftIntensityButton: UIButton!
    @IBOutlet var middleIntensityButton: UIButton!
    @IBOutlet var rightIntensityButton: UIButton!
    
    @IBOutlet var defaultRepetitionsButton: UIButton!
    @IBOutlet var leftRepetitionsButton: UIButton!
    @IBOutlet var middleRepetitionsButton: UIButton!
    @IBOutlet var rightRepetitionsButton: UIButton!
    
    private var repetitions: [Int]!
    private var intensities: [Exercise.ExerciseIntensityKey]!
    private var exercise: Exercise.Exercise!
    
    private var selectedIntensity: Exercise.ExerciseIntensityKey = Defaults.intensities[0]
    private var selectedRepetition: Int = Defaults.repetitions[0]
    
    private func getIntensityButtons() -> [UIButton] {
        return [defaultIntensityButton, leftIntensityButton, middleIntensityButton, rightIntensityButton]
    }
    
    private func getRepetitionButtons() -> [UIButton] {
        return [defaultRepetitionsButton, leftRepetitionsButton, middleRepetitionsButton, rightRepetitionsButton]
    }
    
    ///
    /// Update this cell with the given ``exercise``
    ///
    func setExercise(exercise: Exercise.Exercise) {
        titleLabel.text = exercise.name
        // TODO: Once statistics are wired in, show the exercise.intensity, exericse.metric and exercise.repetitions
        
        repetitions = Defaults.repetitions
        intensities = Defaults.intensities
        
        let allStates = UIControlState.Normal | UIControlState.Highlighted | UIControlState.Selected
        
        getIntensityButtons().zipWithIndex().foreach { (i, button) -> Void in
            button.setTitle(self.intensities[i].intensity.title, forState: allStates)
            button.tag = i
            button.backgroundColor = self.nonSelectedButtonColor
        }
        defaultIntensityButton.backgroundColor = selectedButtonColor
        selectedIntensity = intensities[0]
        
        getRepetitionButtons().zipWithIndex().foreach { (i, button) -> Void in
            button.setTitle(self.intensities[i].intensity.title, forState: allStates)
            button.tag = i
            button.backgroundColor = self.nonSelectedButtonColor
        }
        defaultRepetitionsButton.backgroundColor = selectedButtonColor
        selectedRepetition = repetitions[0]
        
        self.exercise = exercise
    }
    
    @IBAction
    func repetition(sender: UIButton) {
        selectedRepetition = repetitions[sender.tag]
        getRepetitionButtons().foreach { button in
            button.backgroundColor = self.nonSelectedButtonColor
        }
        sender.backgroundColor = selectedButtonColor
    }
    
    @IBAction
    func intensity(sender: UIButton) {
        selectedIntensity = intensities[sender.tag]
        getIntensityButtons().foreach { button in
            button.backgroundColor = self.nonSelectedButtonColor
        }
        sender.backgroundColor = selectedButtonColor
    }
    
    @IBAction
    func done(sender: UIButton) {
        delegate?.doneTagging(exercise, intensity: selectedIntensity, repetition: selectedRepetition)
    }
    
}

///
/// Displays the cell of live classification exercises.
///
class LiveSessionClassificationCell : UITableViewCell {
    
    @IBOutlet var titleLabel: UILabel!

    private var exercise: Exercise.Exercise!

    ///
    /// Update this cell with the given ``exercise``
    ///
    func setExercise(exercise: Exercise.Exercise) {
        titleLabel.text = exercise.name
        self.exercise = exercise
    }
    
}

class LiveSessionClassificationController : UITableViewController, ExerciseSessionSettable, LiveSessionClassificationTagDelegate {
    private var isTagging = false
    private var classificationExamples: [Exercise.Exercise] = []
    private var session: ExerciseSession!
    private var selectedIndexPath: NSIndexPath?
    
    @IBOutlet weak var tagView: LiveSessionTagView!
    
    override func viewDidLoad() {
        tagView.hidden = true
        tagView.delegate = self
        super.viewDidLoad()
    }
    
    // MARK: ExerciseSessionSettable implementation
    func setExerciseSession(session: ExerciseSession) {
        self.session = session
        self.session.getClassificationExamples { x in
            let examples = x.cata(
                { _ -> [Exercise.Exercise] in
                    return ClassificationExamplesCache.sharedInstance.getExamplesFor(session.props)
                }
                , r: { (result: [Exercise.Exercise]) -> [Exercise.Exercise] in
                    ClassificationExamplesCache.sharedInstance.addExamplesToCache(session.props, values: result)
                    return result
                })
            self.classificationExamples = examples
            self.tableView.reloadData()
        }
    }
    
    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return 1
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return 40
    }
    
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if isTagging {
            return 0
        } else {
            return classificationExamples.count
        }
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        switch (indexPath.section, indexPath.row) {
        case (0, let x):
            let cell = tableView.dequeueReusableCellWithIdentifier("manual") as LiveSessionClassificationCell
            cell.setExercise(classificationExamples[x])
            return cell
        default: fatalError("Match error")
        }
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        if selectedIndexPath == .Some(indexPath) {
            session.endExplicitClassification()
            selectedIndexPath = nil
        } else {
            if selectedIndexPath != nil { session.endExplicitClassification() }
            let exercise = classificationExamples[indexPath.row]
            session.startExplicitClassification(exercise.name)
            selectedIndexPath = indexPath
            isTagging = true
            tagView.setExercise(exercise)
            tagView.hidden = false
        }
        tableView.reloadData()
    }
    
    // MARK: LiveSessionClassificationTagDelegate code
    
    private func sendClassification(actualExercise: Exercise.Exercise, count: Int) {
        if count > 0 {
            session.markExerciseExplicit(actualExercise) { _ in self.sendClassification(actualExercise, count: count - 1) }
        } else {
            session.endExplicitClassification()
        }
    }
    
    func doneTagging(exercise: Exercise.Exercise, intensity: Exercise.ExerciseIntensityKey, repetition: Int) {
        let actualExercise = Exercise.Exercise(name: exercise.name, intensity: intensity, metric: nil)
        sendClassification(actualExercise, count: repetition)
        
        isTagging = false
        tagView.hidden = true
        tableView.reloadData()
    }
    
}
