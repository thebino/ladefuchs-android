package app.ladefuchs.android.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import app.ladefuchs.android.BuildConfig
import app.ladefuchs.android.R
import app.ladefuchs.android.dataClasses.ChargeCards
import app.ladefuchs.android.ui.chargecards.ChargeCardDetail
import com.makeramen.roundedimageview.RoundedImageView
import java.io.File
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import kotlin.math.ceil


private var cardWidth: Int = getScreenWidth() / 5
private var cardHeight: Int = 176 * cardWidth / 280
private const val cardMarginLeft: Int = 50
private const val cardMarginRight: Int = 20
private const val cardMarginTop: Int = 20
private const val cardMarginBottom: Int = 20
private const val cardMargin: Int = 20
private const val globalCornerRadius: Float = 15F

/**
 * This function will draw the scrollable list of chargecards
 */
fun drawChargeCard(
    textToDraw: String = "N/A",
    textSize: Float = 8F,
    textColor: Int = Color.BLACK,
    rectangleColor: Int = Color.LTGRAY,
    backgroundColor: Int = Color.WHITE,
    paintStroke: Boolean = false
): Bitmap? {
    val scaleFactor = 2
    val cornerRadius = globalCornerRadius * scaleFactor
    val strokeWidth = 3F * scaleFactor
    val textCardWidth = cardWidth * scaleFactor
    val textCardHeight = cardHeight * scaleFactor
    val cardTextSize = textSize * scaleFactor

    val bitmap = Bitmap.createBitmap(
        textCardWidth,
        textCardHeight,
        Bitmap.Config.ARGB_8888
    )

    // canvas to drawing
    val canvas = Canvas(bitmap)

    // draw rounded rectangle on canvas
    val rectF = RectF(
        strokeWidth,
        strokeWidth,
        canvas.width - strokeWidth,
        canvas.height - strokeWidth
    )

    if (paintStroke) {
        canvas.drawRoundRect(
            rectF,
            cornerRadius,
            cornerRadius,
            Paint().apply { color = rectangleColor })
    }

    val fillPaint = Paint()
    val strokePaint = Paint()

    // fill
    fillPaint.style = Paint.Style.FILL
    fillPaint.color = manipulateColor(backgroundColor, 1.25f)

    // stroke
    strokePaint.style = Paint.Style.STROKE
    strokePaint.color = rectangleColor
    strokePaint.strokeWidth = strokeWidth

    // Second rectangle
    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)    // fill
    if (paintStroke) {
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)  // stroke
    }

    // text paint to draw text
    val textPaint = TextPaint().apply {
        color = textColor
        textAlign = Paint.Align.LEFT
        this.textSize = cardTextSize - strokeWidth / scaleFactor
        isAntiAlias = true
    }
    textPaint.typeface = Typeface.create("Roboto", Typeface.BOLD)

    //calculating if breaking is needed in order to correctly position text on the Y Axis
    val numOfChars = textPaint.breakText(
        textToDraw,
        true,
        rectF.width(),
        null
    )

    val textLines = ceil(textToDraw.length.toDouble() / numOfChars.toDouble())

    var textPositionYOffset =
        canvas.height.toDouble() / 2.0 - (textLines * cardTextSize.toDouble() / 2.0 + 2.0 * strokeWidth.toDouble())

    // draw multiline card
    canvas.drawMultilineText(
        textToDraw,
        textPaint,
        textCardWidth,
        (textCardWidth / 7 - 12).toFloat(),
        textPositionYOffset.toFloat()
    )

    return bitmap
}

/**
 * This function will fill a chargecard with its content
 */
@SuppressLint("ResourceAsColor")
fun fillCards(
    currentType: String,
    chargeCards: List<ChargeCards>,
    maxListLength: Int,
    context: Context,
    view: View,
    api: API,
    resources: Resources,
    paintStroke: Boolean = false,
): Boolean {
    printLog("Filling cards for $currentType")
    val cardMetadata = readCardMetadata(context)
    var cardsDownloaded = false
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val hasADACPrices = prefs!!.getBoolean("specialEnbwAdac", false)
    val hasCustomerMaingauPrices = prefs.getBoolean("specialMaingauCustomer", false)
    // Define Views to attach Card Tables to and required Variables
    var columnSide = "left"
    if (currentType.lowercase() == "dc") {
        columnSide = "right"
    }
    var i = 0
    val columnName = "chargeCardsTableHolder" + currentType.uppercase()
    val chargeCardsColumn: LinearLayout =
        view.findViewById(
            resources.getIdentifier(
                columnName,
                "id",
                context.packageName
            )
        ) ?: return false
    chargeCardsColumn.removeAllViews()
    chargeCards.forEach { currentCard ->

        val cardIdentifier = "card_" + currentCard.identifier
        val cardProviderIdentifier = "card_" + currentCard.provider

        // Skip ADAC card if not enabled
        if (currentCard.identifier == "adac" && !hasADACPrices) {
            printLog("ADAC prices will be skipped")
            return@forEach
        }

        // Skip Maingau Prices if personalized processes are available
        if (currentCard.identifier == "maingau_energie" && hasCustomerMaingauPrices) {
            return@forEach
        }

        var cardMeta = cardMetadata?.find { it.identifier == cardIdentifier }
        if (cardMeta == null) {
            cardMeta = cardMetadata?.find { it.identifier == "default" }
        }


        // Creating a Holder for Card and Price, to lay them out next to each other
        val CardHolderView: LinearLayout = LinearLayout(context)
        chargeCardsColumn.addView(CardHolderView)
        printLog("Creating CardHolderView for $currentType")
        CardHolderView.gravity = Gravity.CENTER_VERTICAL
        CardHolderView.orientation = LinearLayout.HORIZONTAL

        val backgroundUri: String = if (i % 2 == 0) {
            "@drawable/border_light_bg_$columnSide"
        } else {
            "@drawable/border_dark_bg_$columnSide"
        }

        CardHolderView.setBackgroundResource(
            resources.getIdentifier(
                backgroundUri, "drawable",
                context.packageName
            )
        )

        val bundle = bundleOf("cardData" to currentCard)

        CardHolderView.setOnClickListener { view ->
            view.findNavController().navigate(R.id.action_card_to_detail, bundle)
        }

        // Creating a View that will Hold the card image as a Background
        val imageView: ImageView = ImageView(context)
        CardHolderView.addView(imageView)
        imageView.requestLayout()
        imageView.layoutParams.width = cardWidth
        imageView.layoutParams.height = cardHeight


        var resourceIdentifier: Int? = context.resources?.getIdentifier(
            cardIdentifier,
            "drawable",
            context.packageName
        )

        if (resourceIdentifier == 0) {
            resourceIdentifier = context.resources?.getIdentifier(
                cardProviderIdentifier,
                "drawable",
                context.packageName
            )
        }

        var cardImagePath: File? = null

        if (!currentCard.image.isNullOrEmpty()){
            var cardUri = URL(currentCard.image)
            cardImagePath = getImagePath(cardUri, context)
        }

        val cardImageExists = cardImagePath != null  && cardImagePath.exists()

        if ((resourceIdentifier != 0 && resourceIdentifier != null) || cardImageExists) {
            CardHolderView.removeView(imageView)
            val imageCardView = RoundedImageView(context)
            CardHolderView.addView(imageCardView)
            imageCardView.layoutParams.width = cardWidth
            imageCardView.layoutParams.height = cardHeight
            (imageCardView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(
                cardMarginLeft,
                cardMarginTop,
                cardMarginRight,
                cardMarginBottom
            )
            imageCardView.scaleType = ImageView.ScaleType.FIT_XY
            imageCardView.cornerRadius = globalCornerRadius
            if (paintStroke) {
                imageCardView.borderWidth = 4.toFloat()
                imageCardView.borderColor = Color.DKGRAY
            }
            imageCardView.mutateBackground(true)


            //printLog("Card image file $cardImagePath")
            if (cardImageExists) {
                var cardImageDrawable: Drawable? = null
                try {
                    cardImageDrawable =
                        Drawable.createFromPath(cardImagePath!!.absolutePath)!! as BitmapDrawable
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG){
                        e.printStackTrace()
                    }
                }

                if (cardImageDrawable != null) {
                    imageCardView.background =
                        Drawable.createFromPath(cardImagePath!!.absolutePath)!! as BitmapDrawable
                }
            } else {
                resourceIdentifier?.let { imageCardView.setBackgroundResource(it) }
            }

            imageCardView.isOval = false
            imageCardView.elevation = 25.0F
            imageCardView.outlineProvider = OutlineProvider(5,0)

            imageCardView.tileModeX = Shader.TileMode.CLAMP
            imageCardView.tileModeY = Shader.TileMode.CLAMP
            imageCardView.requestLayout()

        } else {
            if (!currentCard.image.isNullOrEmpty()) {
                api.downloadImageToInternalStorage(
                    URL(currentCard.image)
                )
                cardsDownloaded = true
            }
            var cardText = currentCard.name
            if (currentCard.provider != currentCard.name) {
                cardText = currentCard.provider
            }
            val cardBitmap = drawChargeCard(
                textToDraw = cardText,
                textSize = 26F,
                textColor = Color.parseColor("#" + cardMeta?.textColor),
                rectangleColor = Color.parseColor("#" + cardMeta?.borderColor),
                backgroundColor = Color.parseColor("#" + cardMeta?.backgroundColor),
                paintStroke = paintStroke
            )
            imageView.background = BitmapDrawable(resources, cardBitmap)
            imageView.elevation = 30.0F
            imageView.outlineProvider = OutlineProvider(10,10)
            (imageView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(
                cardMarginLeft,
                cardMarginTop,
                cardMarginRight,
                cardMarginBottom
            )
        }

        // Format the price according to the user set locale
        val priceNumberFormat = NumberFormat.getCurrencyInstance()
        val decimalFormatSymbols: DecimalFormatSymbols =
            (priceNumberFormat as DecimalFormat).decimalFormatSymbols
        decimalFormatSymbols.currencySymbol = ""
        priceNumberFormat.decimalFormatSymbols = decimalFormatSymbols
        (priceNumberFormat.format(currentCard.price).trim { it <= ' ' })

        // Creating the TextView that will hold the Price
        val textviewPrice: TextView = TextView(context)
        CardHolderView.addView(textviewPrice)
        textviewPrice.text = priceNumberFormat.format(currentCard.price).trim { it <= ' ' }
        textviewPrice.setTextAppearance(R.style.TableTextView)
        textviewPrice.gravity = Gravity.CENTER
        textviewPrice.width = cardWidth
        i++
    }

    //Filling Column with empty Cells if necessary
    if (i < maxListLength - 1) {
        while (i < maxListLength - 1) {
            // Creating a Holder for Card and Price, to lay them out next to each other
            val CardHolderView: LinearLayout = LinearLayout(context)
            chargeCardsColumn.addView(CardHolderView)
            CardHolderView.gravity = Gravity.CENTER_VERTICAL
            CardHolderView.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            CardHolderView.orientation = LinearLayout.HORIZONTAL
            if (i % 2 == 0) {
                //TableColorLight
                CardHolderView.setBackgroundColor(Color.parseColor("#F0EBDC"))
            } else {
                // TableColorDark
                CardHolderView.setBackgroundColor(Color.parseColor("#E0D7C8"))
            }
            CardHolderView.setPadding(
                cardMarginLeft,
                cardMarginTop,
                cardMarginRight,
                cardMarginBottom
            )

            // Creating the TextView that will hold the Price
            val textview: TextView = TextView(context)
            CardHolderView.addView(textview)
            textview.text = ("")
            textview.setPadding(
                cardMarginLeft,
                cardMarginTop,
                cardMarginRight,
                cardMarginBottom
            )
            textview.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            textview.setTextAppearance(R.style.TableTextViewDisabled)
            textview.setTextColor(R.color.TextColorDisabled)
            textview.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            textview.height = cardHeight
            i++
        }
    }
    printLog("Cards populated for $currentType")
    return cardsDownloaded
}

