package co.q64.cs6068

import com.varabyte.konsole.foundation.anim.konsoleAnimOf
import com.varabyte.konsole.foundation.input.Completions
import com.varabyte.konsole.foundation.input.input
import com.varabyte.konsole.foundation.input.onInputEntered
import com.varabyte.konsole.foundation.input.runUntilInputEntered
import com.varabyte.konsole.foundation.konsoleApp
import com.varabyte.konsole.foundation.konsoleVarOf
import com.varabyte.konsole.foundation.text.cyan
import com.varabyte.konsole.foundation.text.green
import com.varabyte.konsole.foundation.text.magenta
import com.varabyte.konsole.foundation.text.red
import com.varabyte.konsole.foundation.text.text
import com.varabyte.konsole.foundation.text.textLine
import com.varabyte.konsole.foundation.text.white
import com.varabyte.konsole.foundation.text.yellow
import com.varabyte.konsole.runtime.KonsoleApp
import com.varabyte.konsole.terminal.SystemTerminal
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

data class Result(
    val number: BigInteger,
    val prime: Boolean,
    val time: Duration,
    val parallel: Boolean,
    val witnesses: Int,
    val r: Int,
    val d: BigInteger
)

enum class MainMenuOption(
    val hint: String
) {
    TestNumberSequential("Test the primality of a number (sequential)"),
    TestNumberParallel("Test the primality of a number (parallel)"),
    Benchmark("Test the primality of a number (sequential then parallel)"),
    Error("Compute the number of witnesses needed for confidence level"),
    FindPrime("Locate a probable prime of a given number of digits")
}

fun main() {
    konsoleApp(SystemTerminal()) {
        konsole {
            yellow(isBright = true)
            textLine("Welcome to our CS6068 Parallel Computing Final Project!")
        }.run()
        while (true) {
            var selected by konsoleVarOf(MainMenuOption.TestNumberSequential)
            konsole {
                white()
                textLine("What do you want to do?")
                textLine()
                MainMenuOption
                    .values()
                    .forEach { option ->
                        cyan()
                        text("  ${option.ordinal + 1}")
                        white()
                        text(": ${option.hint}")
                        textLine()
                    }
                textLine()
                white()
                text("> ")
                input(Completions(*MainMenuOption.values().map { "${it.ordinal + 1}" }.toTypedArray()))
            }
                .runUntilInputEntered {
                    onInputEntered {
                        selected = MainMenuOption.values()[input.toInt() - 1]
                    }
                }
            when (selected) {
                MainMenuOption.TestNumberSequential -> inputAndTest(parallel = false)
                MainMenuOption.TestNumberParallel -> inputAndTest(parallel = true)
                MainMenuOption.Benchmark -> inputAndTest(benchmark = true)
                MainMenuOption.Error -> computeWitnesses()
                MainMenuOption.FindPrime -> findPrime()
            }
        }
    }

    exitProcess(0)
}

@OptIn(ExperimentalTime::class)
fun KonsoleApp.findPrime() {
    var digits by konsoleVarOf(0)
    var error by konsoleVarOf(0.0)

    konsole {
        white()
        text("Enter the number of digits: ")
        input()
    }
        .runUntilInputEntered {
            onInputEntered {
                digits = input.toInt()
            }
        }

    konsole {
        white()
        text("Enter the acceptable error probability: ")
        input()
    }
        .runUntilInputEntered {
            onInputEntered {
                error = input.toDouble()
            }
        }

    val updated = TimeSource.Monotonic.markNow()
    var found: BigInteger? = null
    var tried = 0

    konsole {
        val animation = konsoleAnimOf(listOf("\\", "|", "/", "-"), 125.milliseconds.toJavaDuration())
        val thinkingAnim = konsoleAnimOf(listOf("", ".", "..", "..."), 500.milliseconds.toJavaDuration())
        textLine()
        white()
        text(animation)
        text(" Searching for prime")
        text(thinkingAnim)
        magenta(isBright = true)
        text(" (Tried $tried)")
        textLine()
    }
        .run {
            generateSequence { Random.nextInt(1, 10) }
                .chunked(digits)
                .map { BigInteger(it.joinToString("")) }
                .takeWhile { number ->
                    val prime = testQuiet(number, computeWitnessesForError(number, error), parallel = true)
                    tried++
                    if (updated.elapsedNow() > 250.milliseconds) {
                        rerender()
                    }

                    if (prime) {
                        found = number
                    }

                    !prime
                }
                .lastOrNull()
            rerender()
        }

    konsole {
        textLine()
        white()
        text("Found prime: ")
        green(isBright = true)
        textLine("$found")
        textLine()
    }
        .run()
}

fun KonsoleApp.computeWitnesses() {
    var number by konsoleVarOf(BigInteger.TWO)
    var error by konsoleVarOf(0.0)

    konsole {
        white()
        text("Enter the number to analyze: ")
        input()
    }
        .runUntilInputEntered {
            onInputEntered {
                number = input.toBigInteger()
            }
        }

    konsole {
        white()
        text("Enter the acceptable error probability: ")
        input()
    }
        .runUntilInputEntered {
            onInputEntered {
                error = input.toDouble()
            }
        }

    konsole {
        textLine()
        white()
        text("Number of witnesses needed: ")
        red(isBright = true)
        textLine("${computeWitnessesForError(number, error)}")
        textLine()

    }
        .run()
}

fun KonsoleApp.inputAndTest(parallel: Boolean = false, benchmark: Boolean = false) {
    var number by konsoleVarOf(BigInteger.TWO)
    var witnesses by konsoleVarOf(0)

    konsole {
        white()
        text("Enter the number to test: ")
        input()
    }
        .runUntilInputEntered {
            onInputEntered {
                number = input.toBigInteger()
            }
        }

    konsole {
        white()
        text("Enter the number of witnesses (type 0 for automatic): ")
        input(Completions("0"))
    }
        .runUntilInputEntered {
            onInputEntered {
                witnesses = input.toIntOrNull() ?: 0
            }
        }

    fun run(parallel: Boolean) {
        testAndOutput(
            number,
            if (witnesses < 2) ln(number.toDouble()).pow(2.0).roundToInt().coerceAtLeast(2) else witnesses,
            parallel
        )
    }

    if (benchmark) {
        run(false)
        run(true)
    } else {
        run(parallel)
    }
}

fun KonsoleApp.testAndOutputSequentialParallel(number: BigInteger, witnesses: Int) {
    testAndOutput(number, witnesses, false)
    testAndOutput(number, witnesses, true)
}

fun KonsoleApp.testAndOutput(number: BigInteger, witnesses: Int, parallel: Boolean = false) {
    val result = test(number, witnesses, parallel)
    outputResult(result)
}

fun KonsoleApp.outputResult(result: Result) {
    with(result) {
        val buffer = max(5, 20 - "${result.number}".length)
        konsole {
            cyan()
            text("${"=".repeat(buffer)} ")
            white()
            text("TEST RESULT: ")
            yellow()
            text("$number")
            cyan()
            text(" ${"=".repeat(buffer)}")
            textLine()

            white()
            text("Probably Prime: ")
            if (prime) {
                green(isBright = true)
                textLine("Yes!")
            } else {
                red()
                textLine("No (Definitely Composite)")
            }

            white()
            text("Parallel: ")
            if (parallel) {
                cyan(isBright = true)
                textLine("Yes! (${Runtime.getRuntime().availableProcessors()} threads)")
            } else {
                red()
                textLine("No (Sequential Test)")
            }

            white()
            text("Error Probability: ")
            yellow()
            val error = errorProbability(result.number, result.witnesses)
            if (!result.prime) {
                textLine("N/A")
            } else if (error == 0.0) {
                textLine("$error (too small to represent with a 64-bit floating point)")
            } else {
                textLine("$error")
            }


            white()
            text("Total Time: ")
            cyan()
            textLine("${result.time}")

            cyan()
            textLine("=".repeat(buffer * 2 + 15 + "${result.number}".length))
            textLine()
        }.run()
    }
}

val PowersOfTwo = (0..100)
    .map { BigInteger.TWO.pow(it) }

val Sequential = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

val Parallel = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()

@OptIn(ExperimentalTime::class)
fun KonsoleApp.test(number: BigInteger, witnesses: Int, parallel: Boolean = false): Result {
    val start = TimeSource.Monotonic.markNow()
    var lastRender = TimeSource.Monotonic.markNow()
    val complete = AtomicInteger(0)
    var result: Result? = null
    konsole {
        textLine()
        val percent = complete.get() / witnesses.toDouble()
        yellow(isBright = true)
        text("Running test: ")
        white()
        text("$complete".padStart("$witnesses".length, '0'))
        cyan()
        text("/")
        white()
        text("$witnesses")

        white()
        text(" [")
        val stops = 30
        (0 until stops).forEach { index ->
            if (index <= percent * stops) {
                if ((index + 1) <= percent * stops) {
                    red()
                    text("=")
                } else {
                    yellow()
                    text(">")
                }
            } else {
                text(" ")
            }
        }
        white()
        text("] ")
        cyan()
        text(start.elapsedNow().toString(DurationUnit.SECONDS, 2))

        textLine()
        textLine()
    }
        .run {
            val r = PowersOfTwo
                .indexOfLast { power -> (number - BigInteger.ONE) % power == BigInteger.ZERO } - 1
            val d = (number - BigInteger.ONE) / PowersOfTwo[r]
            var prime = false
            val time = measureTimeMillis {
                prime = if (number % BigInteger.TWO == BigInteger.ZERO) false
                else
                    runBlocking {
                        (1..witnesses)
                            .map {
                                async(if (parallel) Parallel else Sequential) {
                                    generateSequence { Random.nextInt(10) }
                                        .chunked("$number".length)
                                        .map { it.joinToString("") }
                                        .map { BigInteger(it) }
                                        .filter { it >= BigInteger.TWO && it < number - BigInteger.ONE }
                                        .first()
                                        .let {
                                            testWitness(number, r, d, it).also {
                                                complete.incrementAndGet()
                                                if (lastRender.elapsedNow() > 250.milliseconds) {
                                                    rerender()
                                                    lastRender = TimeSource.Monotonic.markNow()
                                                }
                                            }
                                        }
                                }
                            }
                            .awaitAll()
                            .all { it }
                    }
                rerender()
            }
            result = Result(
                number = number,
                prime = prime,
                time = time.milliseconds,
                parallel = parallel,
                witnesses = witnesses,
                r = r,
                d = d
            )
        }
    return result!!
}

fun testQuiet(number: BigInteger, witnesses: Int, parallel: Boolean = false) = runBlocking {
    if (number % BigInteger.TWO == BigInteger.ZERO) return@runBlocking false
    val r = PowersOfTwo
        .indexOfLast { power -> (number - BigInteger.ONE) % power == BigInteger.ZERO } - 1
    val d = (number - BigInteger.ONE) / PowersOfTwo[r]

    (1..witnesses)
        .map {
            async(if (parallel) Parallel else Sequential) {
                generateSequence { Random.nextInt(10) }
                    .chunked("$number".length)
                    .map { it.joinToString("") }
                    .map { BigInteger(it) }
                    .filter { it >= BigInteger.TWO && it < number - BigInteger.ONE }
                    .first()
                    .let {
                        testWitness(number, r, d, it)
                    }
            }
        }
        .awaitAll()
        .all { it }
}

fun testWitness(number: BigInteger, r: Int, d: BigInteger, witness: BigInteger): Boolean {
    var x = witness.modPow(d, number)
    if (x == BigInteger.ONE || x == number - BigInteger.ONE) return true
    repeat(r - 1) {
        x = x.modPow(BigInteger.TWO, number)
        if (x == number - BigInteger.ONE) return true
    }
    return false
}

fun computeWitnessesForError(number: BigInteger, error: Double) = generateSequence(0) { it + 1 }
    .takeWhile { errorProbability(number, it) > error }
    .last() + 1

fun errorProbability(number: BigInteger, witnesses: Int) = 1.0 /
        (1.0 + (1.0 / 4.0.pow(-witnesses)) * (number.probabilityIsPrime / (1.0 - number.probabilityIsPrime)))

val BigInteger.probabilityIsPrime get() = 1.0 / ln(toDouble())