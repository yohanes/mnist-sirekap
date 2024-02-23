package com.compactbyte.digits;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.TensorFlowLite;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    //a simple drawing view for drawing digits using finger
    //from https://stackoverflow.com/questions/16650419/draw-in-canvas-by-finger-android
    FingerDrawingView dv;
    Interpreter tflite;
    Interpreter tflite_accurate;

    Interpreter tflite_blank;

    ArrayList<Interpreter> tflite_ensemble = new ArrayList<>();

    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private final int applyHeuristic(final float[] probabilities) {
        //find max
        int firstMax = 0;
        for (int i = 0; i < 10; i++) {
            if (probabilities[i] > probabilities[firstMax]) {
                firstMax = i;
            }
        }
        //find second max
        int secondMax = 0;
        for (int i = 0; i < 10; i++) {
            if (probabilities[i] > probabilities[secondMax] && i != firstMax) {
                secondMax = i;
            }
        }
        if (firstMax != 0 || probabilities[0] >= 10.0d) {
            if (firstMax != 1 || probabilities[1] >= 14.5d) {
                if (firstMax == 3 && probabilities[3] < 10.0d && probabilities[9] > 3.0d) {
                    return 9;
                }
            } else if (probabilities[secondMax] > 0.0d) {
                return secondMax;
            }
        } else if (secondMax == 8) {
            return secondMax;
        }
        return firstMax;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView credits = (TextView) findViewById(R.id.credits);
        credits.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

        //get the clear button and the drawing view
        dv = (FingerDrawingView) findViewById(R.id.fingerdrawing);
        final WebView wv = (WebView) findViewById(R.id.webview);
        //set the clear button to clear the drawing view
        findViewById(R.id.clear_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dv.clearCanvas();
                StringBuilder sb = new StringBuilder();
                //Construct html to display the digit
                sb.append("<html><body><h1>");
                sb.append("</h1></body></html>");
                wv.loadData(sb.toString(), "text/html", "utf-8");

            }
        });
        //// Load the TF Lite model from the asset folder.
        try {
            tflite = new Interpreter(loadModelFile(getAssets(), "mnist.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            tflite_blank = new Interpreter(loadModelFile(getAssets(), "blank_detection_model.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            tflite_accurate = new Interpreter(loadModelFile(getAssets(), "accurate.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            for (int i = 0; i < 15; i++) {
                tflite_ensemble.add(new Interpreter(loadModelFile(getAssets(), "ensemble-15-mnist/ensemble_model_" + i + ".tflite")));
                tflite_ensemble.get(i).allocateTensors();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        dv.addTouchUpListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //get the bitmap from the drawing view

                // Read input shape from model file
                int imageTensorIndex = 0;
                int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape();
                int imageSizeX = imageShape[1];
                int imageSizeY = imageShape[2];
                int numBytesPerChannel = 4;
                ByteBuffer imgData = dv.getAsByteBuffer(imageSizeX, imageSizeY);

                Tensor outputTensor = tflite.getOutputTensor(0);
                //output
                float[][] output = new float[outputTensor.shape()[0]][outputTensor.shape()[1]];

                tflite.run(imgData, output);
                //find the digit with the highest probability
                int maxDigit = 0;
                float prob1 = output[0][0];
                for (int i = 0; i < output[0].length; i++) {
                    if (output[0][i] > output[0][maxDigit]) {
                        maxDigit = i;
                        prob1 = output[0][i];
                    }
                }

                //run second model
                tflite_accurate.run(imgData, output);
                //find the digit with the highest probability
                int maxDigit2 = 0;
                float prob2 = output[0][0];
                for (int i = 0; i < output[0].length; i++) {
                    if (output[0][i] > output[0][maxDigit2]) {
                        maxDigit2 = i;
                        prob2 = output[0][i];
                    }
                }

                //run blank detection model
                outputTensor = tflite_blank.getOutputTensor(0);
                output = new float[outputTensor.shape()[0]][outputTensor.shape()[1]];
                tflite_blank.run(imgData, output);
                //find max value
                int maxDigitBlank = 0;
                float probBlank = output[0][0];
                for (int i = 0; i < output[0].length; i++) {
                    if (output[0][i] > output[0][maxDigitBlank]) {
                        maxDigitBlank = i;
                        probBlank = output[0][i];
                    }
                }
                boolean isBlank = !(probBlank < 1.0);

                //ensemble
                outputTensor = tflite_ensemble.get(0).getOutputTensor(0);
                output = new float[outputTensor.shape()[0]][outputTensor.shape()[1]];
                float tmpoutput[] = new float[10];
                //run ensemble
                for (int i = 0; i < tflite_ensemble.size(); i++) {
                    tflite_ensemble.get(i).run(imgData, output);
                    for (int j = 0; j < output[0].length; j++) {
                        tmpoutput[j] += output[0][j];
                    }
                }
                int maxDigit3 = applyHeuristic(tmpoutput);
                float prob3 = tmpoutput[maxDigit3];


                StringBuilder sb = new StringBuilder();
                //Construct html to display the digit
                sb.append("<html><body>");
                //create a table
                sb.append("<table border=\"1\">");
                //add the first row
                sb.append("<tr>");
                sb.append("<td>Model</td>");
                sb.append("<td>Digit</td>");
//                sb.append("<td>Probability</td>");
                sb.append("</tr>");
                //add the first row
                sb.append("<tr>");
                sb.append("<td>Base model</td>");
                sb.append("<td>");
                sb.append(maxDigit);
                sb.append("</td>");
//                sb.append("<td>");
//                sb.append(prob1);
//                sb.append("</td>");
                sb.append("</tr>");
                //add the second row
                sb.append("<tr>");
                sb.append("<td>Sirekap</td>");
                sb.append("<td>");
                sb.append(maxDigit2);
                sb.append("</td>");
//                sb.append("<td>");
//                sb.append(prob2);
//                sb.append("</td>");
                sb.append("</tr>");
                //add the third row
                sb.append("<tr>");
                sb.append("<td>Sirekap Ensemble</td>");
                sb.append("<td>");
                if (isBlank) {
                    sb.append("Blank");
                } else {
                    sb.append(maxDigit3);
                }
                sb.append("</td>");
//                sb.append("<td>");
//                sb.append(prob3);
//                sb.append("</td>");
                sb.append("</tr>");

                sb.append("<tr>");
                sb.append("<td>Blank (X) probability</td>");
                sb.append("<td>");
                sb.append(probBlank);
                sb.append("</td>");
                sb.append("</tr>");

                //close the table
                sb.append("</table>");

                sb.append("</body></html>");
                wv.loadData(sb.toString(), "text/html", "utf-8");

            }
        });

    }
}