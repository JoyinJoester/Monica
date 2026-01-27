package takagi.ru.monica.data

/**
 * Security questions for password recovery
 */
data class SecurityQuestion(
    val id: Int,
    val questionText: String
)

data class SecurityQuestionAnswer(
    val questionId: Int,
    val questionText: String,
    val answer: String
)

data class SecurityQuestionsSetup(
    val question1: SecurityQuestionAnswer,
    val question2: SecurityQuestionAnswer
)

object PredefinedSecurityQuestions {
    val questions = listOf(
        SecurityQuestion(1, "What was the name of your first pet?"),
        SecurityQuestion(2, "What is your mother's maiden name?"),
        SecurityQuestion(3, "In what city were you born?"),
        SecurityQuestion(4, "What was the name of your elementary school?"),
        SecurityQuestion(5, "What is your favorite movie?"),
        SecurityQuestion(6, "What was your first car model?"),
        SecurityQuestion(7, "What is the name of your best friend from childhood?"),
        SecurityQuestion(8, "What was your favorite food as a child?"),
        SecurityQuestion(9, "What is the name of the street you grew up on?"),
        SecurityQuestion(10, "What was your high school mascot?")
    )
    
    val questionsZh = listOf(
        SecurityQuestion(1, "您第一只宠物的名字是什么？"),
        SecurityQuestion(2, "您母亲的娘家姓是什么？"),
        SecurityQuestion(3, "您出生在哪个城市？"),
        SecurityQuestion(4, "您的小学校名是什么？"),
        SecurityQuestion(5, "您最喜欢的电影是什么？"),
        SecurityQuestion(6, "您的第一辆汽车是什么型号？"),
        SecurityQuestion(7, "您童年最好朋友的名字是什么？"),
        SecurityQuestion(8, "您小时候最喜欢的食物是什么？"),
        SecurityQuestion(9, "您成长的街道名称是什么？"),
        SecurityQuestion(10, "您高中的吉祥物是什么？")
    )
    
    fun getQuestions(isZh: Boolean = false): List<SecurityQuestion> {
        return if (isZh) questionsZh else questions
    }
    
    fun getQuestionById(id: Int, isZh: Boolean = false): SecurityQuestion? {
        return getQuestions(isZh).find { it.id == id }
    }
}