package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test
import java.time.LocalDate

val expressionRegex =
    Regex("""(?<negation>not)?\s?(?<lvalue>distance|date|text) (?<operator>contains|is|equals|less than|greater than|before|after|between) (?<rvalue>\".*?\"|\'.*?\'|[0-9]+ (and|or) [0-9]+|\S+)\s?(?<junction>and|or)?\s?""")

data class Expression(
    val negation: Boolean = true,
    val lvalue: String,
    val operator: String,
    val rvalue: Any,
    val junction: String? = null
) {
    constructor(regexMatchGroups: MatchGroupCollection) : this(
        !regexMatchGroups["negation"]?.value.isNullOrEmpty(),
        regexMatchGroups["lvalue"]!!.value,
        regexMatchGroups["operator"]!!.value,
        when (regexMatchGroups["operator"]!!.value) {
            "between" -> Regex("""(?<lower>[0-9]+) and (?<upper>[0-9]+)""")
                .find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                    arrayOf(it["lower"]!!.value, it["upper"]!!.value)
                }

            else -> Regex("""^['"]?(?<string>.*?)['"]?$""").find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                arrayOf(it["string"]!!.value)
            }
        }!!.map { rvalue ->
            when (regexMatchGroups["lvalue"]!!.value) {
                "distance" -> rvalue.toInt()
                "date" -> LocalDate.parse(rvalue)
                else -> rvalue
            }
        }.let { typedArray ->
            when (typedArray.size) {
                1 -> typedArray[0]
                else -> typedArray
            }
        },
        regexMatchGroups["junction"]?.value,
    )
}

class SearchRegexTest {
    @Test
    fun simpleDistanceExpression() {
        Assert.assertEquals(
            listOf(
                Expression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = 14,
                    junction = null
                )
            ),
            expressionRegex.findAll("distance is 14")
                .map {
                    Expression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleGreaterThanExpression() {
        Assert.assertEquals(
            listOf(
                Expression(
                    negation = false,
                    lvalue = "distance",
                    operator = "greater than",
                    rvalue = 50,
                    junction = null
                )
            ),
            expressionRegex.findAll("distance greater than 50")
                .map {
                    Expression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleDateExpression() {
        Assert.assertEquals(
            listOf(
                Expression(
                    negation = false,
                    lvalue = "date",
                    operator = "before",
                    rvalue = LocalDate.of(2023, 9, 18),
                    junction = null
                )
            ),
            expressionRegex.findAll("date before 2023-09-18")
                .map {
                    Expression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleRangeExpression() {
        Assert.assertEquals(
            listOf(
                Expression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(14, 20),
                    junction = null
                )
            ),
            expressionRegex.findAll("distance between 14 and 20")
                .map {
                    Expression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleTextExpression() {
        Assert.assertEquals(
            listOf(
                Expression(
                    negation = false,
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "flat tire",
                    junction = null
                )
            ),
            expressionRegex.findAll("text contains \"flat tire\"")
                .map {
                    Expression(it.groups)
                }.toList()
        )
    }

    @Test
    fun compoundExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                Expression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = 14,
                    junction = "and"
                ),
                Expression(
                    negation = false,
                    lvalue = "date",
                    operator = "after",
                    rvalue = LocalDate.of(2023, 8, 4),
                    junction = null
                )
            ),
            expressionRegex.findAll("distance is 14 and date after 2023-08-04")
                .map {
                    Expression(it.groups)
                }.toList().toTypedArray()
        )
    }

    @Test
    fun tripleCompoundExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                Expression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = 14,
                    junction = "and"
                ),
                Expression(
                    negation = false,
                    lvalue = "date",
                    operator = "after",
                    rvalue = LocalDate.of(2023, 8, 4),
                    junction = "and"
                ),
                Expression(
                    negation = false,
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "flat tire",
                    junction = null
                )
            ),
            expressionRegex.findAll("distance is 14 and date after 2023-08-04 and text contains \"flat tire\"")
                .map {
                    Expression(it.groups)
                }.toList().toTypedArray()
        )
    }
}