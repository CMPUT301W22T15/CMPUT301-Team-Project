package com.example.qrgameteam15;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * This class is responsible for editing details about the scanned code
 * It allows user to either take a photo of the object or track geolocation
 * Displays score obtained from the code
 * Users can also comment on the image
 */
public class QRCodeEditor extends AppCompatActivity {
    // Initialize variables
    private TextView newScan;
    private TextView score;
    private Button addGeolocation;
    //private Button addPhoto;
    private Button save;
    private ListView commentSection;
    private ArrayList<String> comments;
    private ArrayAdapter<String> commentAdapter;
    private EditText commentInput;
    private Button postComment;
    SingletonPlayer singletonPlayer = new SingletonPlayer();
    FusedLocationProviderClient fusedLocationProviderClient;
    FirebaseFirestore db;
    CollectionReference collectionReference;

    private ImageView imageView;
    private String currentPhotoPath;
    private String filename;
    Uri imageUri = null;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private QRCode QR;
    

    /**
     * This method creates the inital interface and obtains the necessary permissions
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode_editor);

        // Set variable data
        newScan = findViewById(R.id.new_scan);
        score = findViewById(R.id.score);
        addGeolocation = findViewById(R.id.geolocation_option);
        //addPhoto = findViewById(R.id.object_photo_option);
        save = findViewById(R.id.save);
        commentSection = findViewById(R.id.comments);
        commentInput = findViewById(R.id.comment_editor);
        postComment = findViewById(R.id.submit_comment);
        db = FirebaseFirestore.getInstance();
        // Initialize fusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        collectionReference = db.collection("Players");


        // Get intent
        Intent intent = this.getIntent();
//        int scoreValue = getIntent().getIntExtra("scoreValue", 0);
        QR = (QRCode) getIntent().getParcelableExtra("QRCodeValue");
        
        //EL Start - updated lengthQRCode, qrCodeLast to resolve .qrCodes reference error
        int lengthQRCode = singletonPlayer.player.qrCodes.size();
        QRCode qrCodeLast = singletonPlayer.player.qrCodes.get(lengthQRCode-1);


        //EL End - updated lengthQRCode, qrCodeLast to resolve .qrCodes reference error
        
        int scoreValue = qrCodeLast.getScore();
        // Set score value

        score.setText("Score: " + String.valueOf(scoreValue));

        // Create the button listener
        save.setOnClickListener(saveQRCodeData());
        postComment.setOnClickListener(new View.OnClickListener() {
            /**
             * This method overrides the onClick listener for the postComment button
             * It send the view to the addComment method
             * @param view
             * View represents the User Interface for the activity
             */
            @Override
            public void onClick(View view) {
                addComment(view);
            }
        });

        addGeolocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ActivityCompat.checkSelfPermission(QRCodeEditor.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(QRCodeEditor.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
                } else {
                    ActivityCompat.requestPermissions(QRCodeEditor.this,
                            new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 44);
                }
            }
        });

        // Initialize variables for comment section and new comments
        comments = new ArrayList<>();
        commentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, comments);
        commentSection.setAdapter(commentAdapter);

        }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                Location location = task.getResult();
                if (location != null) {
                    try {
                        Geocoder geocoder = new Geocoder(QRCodeEditor.this,
                                Locale.getDefault());
                        List<Address> addresses =geocoder.getFromLocation(
                                location.getLatitude(), location.getLongitude(), 1
                        );

                        String latitudeString = Double.toString(addresses.get(0).getLatitude());
                        String longitudeString = Double.toString(addresses.get(0).getLongitude());
                        int lengthQRCode = singletonPlayer.player.qrCodes.size();
                        String locationString = latitudeString+" "+longitudeString;  //TODO changed "-" to ""
                        QRCode qrCode = singletonPlayer.player.qrCodes.get(lengthQRCode-1);
                        // [0, 1, 2]
                        qrCode.idObject.setLocationStr(locationString);
                        String hashLoc = qrCode.getSha256Hex();
                        qrCode.idObject.setHashedID(hashLoc +"-"+ locationString);
                        qrCode.setLocation(locationString);
                        qrCode.hasLocation = true;
                        singletonPlayer.player.qrCodes.set(lengthQRCode-1, qrCode);
                        String TAG = "working";
                        Toast.makeText(QRCodeEditor.this, "saved geolocation", Toast.LENGTH_SHORT).show();
                        collectionReference
                                .document(singletonPlayer.player.getUsername())
                                .set(singletonPlayer.player)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.d(TAG,"message");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e("MYAPP", "exception: " + e.getMessage());
                                        Log.e("MYAPP", "exception: " + e.toString());
                                    }
                                });
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * This method is executed from the OnClick() listener for the postComment button
     * It will take the text from the EditText and add it to the comment list if valid
     * @param view
     * View represents the User Interface for the activity
     */
    private void addComment(View view) {
        String newComment = commentInput.getText().toString();
        String username = SingletonPlayer.player.getUsername();

        // Check a message has been entered
        if (newComment.trim().length() > 0) {
            //commentAdapter.add(newComment);
            comments.add(username + ": " + newComment);
            commentAdapter.notifyDataSetChanged();
            commentInput.setText("");
        } else {
            Toast.makeText(getApplicationContext(), "Please enter a comment!", Toast.LENGTH_LONG).show();
        }
    }


    private View.OnClickListener saveQRCodeData() {
        return new View.OnClickListener() {
            /**
             * This method will update the QRCode data in the database
             * @param view
             * View represents the User interface for the activity
             */
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(QRCodeEditor.this, UserMenu.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        };
    }
    public void addPhotos(View view) {
        //Intent takePhotoIntent = new Intent(getApplicationContext(), TakePhoto.class );
        //takePhotoIntent.putExtra("QRCodeFromEditor", (Parcelable) QR);
        //startActivity(takePhotoIntent);
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        //EL Start - added 20220321, need testing
        db = FirebaseFirestore.getInstance();
        collectionReference = db.collection("Players");
        // Not using right now because we get the last QR code in firebase - el & manny
        //QR = (QRCode) getIntent().getParcelableExtra("QRCodeFromEditor");
        //EL End - added 20220321, need testing
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Create the File where the photo should go
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            // Error occurred while creating the File
            Toast.makeText(QRCodeEditor.this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
        // Continue only if the File was successfully created
        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(QRCodeEditor.this,
                    "com.example.android.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

            //startActivityForResult deprecated, use activityResult Launcher instead
//            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            activityResultLauncher.launch(takePictureIntent);

        }
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {

                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK){

                        File file = new File(currentPhotoPath);
                        imageUri = Uri.fromFile(file);
                        filename = file.getName();
                        uploadImage();
                        //EL Start - added 20220321, need testing
                        addPhotoToQRCode();
                        //EL End - added 20220321, need testing
                        //Intent intent = new Intent(getApplicationContext(), UserMenu.class);
                        //intent.putExtra("userMenu_session22", (String) null);
                        //startActivity(intent);
                    }
                }
            }
    );
        /**
         * Funcation creates a file to store the photo in full-size
         * @return
         * @throws IOException
         */
        private File createImageFile() throws IOException {
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );

            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = image.getAbsolutePath();
            return image;
        }


        private void uploadImage(){
            String username = SingletonPlayer.player.getUsername();
//        StorageReference imageRef = storageReference.child("images_em/" + fileName);
            StorageReference imageRef = storageReference.child(username + "/" + filename);


            imageRef.putFile(imageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Toast.makeText(QRCodeEditor.this, "Upload successful.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Handle unsuccessful uploads
                            Toast.makeText(QRCodeEditor.this, "Failed to upload" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        //EL Start - added 20220321, need testing
        public void addPhotoToQRCode() {
            int lengthQRCode = SingletonPlayer.player.qrCodes.size();
            QRCode qrCode = SingletonPlayer.player.qrCodes.get(lengthQRCode-1);
            qrCode.setImageIDString(filename);
            qrCode.setHasPhoto(true);
            SingletonPlayer.player.qrCodes.set(lengthQRCode-1, qrCode);
            String TAG = "add photo QR working";
            collectionReference
                    .document(SingletonPlayer.player.getUsername())
                    .set(SingletonPlayer.player)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Log.d(TAG,"message");
                            Toast.makeText(QRCodeEditor.this, "Add to QRCode successful.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("MYAPP", "exception: " + e.getMessage());
                            Log.e("MYAPP", "exception: " + e.toString());
                            Toast.makeText(QRCodeEditor.this, "Add to QRCode FAILED.", Toast.LENGTH_SHORT).show();
                        }
                    });

        }

}