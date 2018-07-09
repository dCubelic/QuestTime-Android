package com.example.android.questtime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;

public class RoomActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener{

    static final int QUESTION_UNANSWERED = 456;
    static final int QUESTION_ANSWERED = 567;

    private static final String TAG = "tag";
    RecyclerView questionsList;

    ArrayList<Question> questions = new ArrayList<>();
    RecyclerQuestionAdapter adapter;
    RecyclerView.LayoutManager manager;

    TextView roomNameTitle;
    TextView roomKeyTextView;
    String roomKey;
    String roomName;
    String roomPrivateKey;

    private ClickSound cs;

    String roomType;
    int points;
    double joined;
    double created;
    int bodovi;

    TextView noQuestionsTxt;

    ImageView lock;
    ImageView peopleButton;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private MediaPlayer mp;
    private SwipeRefreshLayout swipeRefreshLayout;

    private int neodgovorenoPitanje = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.room_activity);
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.questionSwipeLayout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeColors(Color.GRAY, Color.GREEN, Color.BLUE,
                Color.RED, Color.CYAN);
        swipeRefreshLayout.setDistanceToTriggerSync(20);// in dips
        swipeRefreshLayout.setSize(SwipeRefreshLayout.DEFAULT);// LARGE also can be used

        cs = new ClickSound(this);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        peopleButton = (ImageView) findViewById(R.id.peopleBtn);
        roomNameTitle = (TextView) findViewById(R.id.roomNameTitle);
        roomKeyTextView = (TextView) findViewById(R.id.roomKey);
        noQuestionsTxt = (TextView) findViewById(R.id.no_questions_txt);

        lock = (ImageView) findViewById(R.id.lock);

        questionsList = findViewById(R.id.questions_list_view);
        questionsList.setHasFixedSize(true);
        manager = new LinearLayoutManager(this);
        questionsList.setLayoutManager(manager);

        adapter = new RecyclerQuestionAdapter(this, questions, new ItemClickListenerInterface() {
            @Override
            public void onItemClick(View v, int position) {
                cs.start();
                final Question question = (Question) questions.get(position);
                if (question.getPoints() == -1) {
                    Intent intent = new Intent(RoomActivity.this, AnswerActivity.class);
                    intent.putExtra("key", question.getId());
                    intent.putExtra("points", String.valueOf(question.getPoints()));
                    intent.putExtra("category", question.getCategory());
                    intent.putExtra("roomId", roomKey);
                    intent.putExtra("position", position);
                    startActivityForResult(intent, QUESTION_ANSWERED);
                    neodgovorenoPitanje = -1;
                } else {
                    mDatabase.child("rooms").child(roomKey).child("questions").child(question.getId()).child("answers")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    final String myAnswer = dataSnapshot.child(mAuth.getUid()).getValue().toString();
                                    final int numberOfAnswers = (int) dataSnapshot.getChildrenCount();
                                    Intent intentResult = new Intent(RoomActivity.this, ResultActivity.class);
                                    intentResult.putExtra("key", question.getId());
                                    intentResult.putExtra("points", String.valueOf(question.getPoints()));
                                    intentResult.putExtra("category", question.getCategory());
                                    intentResult.putExtra("roomId", roomKey);
                                    intentResult.putExtra("myAnswer", myAnswer);
                                    intentResult.putExtra("numberOfAnswers", numberOfAnswers);
                                    startActivity(intentResult);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                }
            }

            @Override
            public void onLongItemClick(View v, int position) {

            }
        });

        questionsList.addItemDecoration(new VerticalSpaceItemDecoration(20));
        questionsList.setAdapter(adapter);

        roomKey = getIntent().getStringExtra("key");
        roomName = getIntent().getStringExtra("name");
        roomType = getIntent().getStringExtra("type");

        ucitajPitanja();

        if(roomType.equals("public")){
            roomKeyTextView.setVisibility(View.GONE);
            lock.setVisibility(View.GONE);
        } else {
            roomPrivateKey = getIntent().getStringExtra("privateKey");
            roomKeyTextView.setText(roomPrivateKey);

            roomKeyTextView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    ClipboardManager cm = (ClipboardManager)getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData cd = ClipData.newPlainText("Key", roomKeyTextView.getText());
                    cm.setPrimaryClip(cd);
                    Toast.makeText(getApplicationContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();

                    return true;
                }
            });
        }

        roomNameTitle.setText(roomName);

        peopleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mp.start();
                Intent intent = new Intent(RoomActivity.this, PeopleActivity.class);
                intent.putExtra("roomKey", roomKey);
                startActivity(intent);
            }
        });
    }


    public void ucitajPitanja(){
        questions.clear();
        mDatabase.child("rooms").child(roomKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot dataSnapshot) {
                for (final DataSnapshot snapshot: dataSnapshot.child("questions").getChildren()) {
                    joined = Double.parseDouble(dataSnapshot.child("members").child(mAuth.getUid()).getValue().toString());
                    mDatabase.child("questions")
                            .child(snapshot.child("category").getValue().toString())
                            .child(snapshot.getKey().toString()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot1) {
                            created = Double.parseDouble(snapshot.child("timestamp").getValue().toString());
                            try{
                                bodovi = Integer.parseInt(snapshot.child("points").child(mAuth.getUid()).getValue().toString());
                            } catch (NullPointerException e){
                                bodovi = -1;
                            }
                            Question addQuestion = new Question(dataSnapshot1.child("question").getValue().toString(),
                                    created,
                                    bodovi,
                                    snapshot.getKey(),
                                    snapshot.child("category").getValue().toString());

                            if (created > joined && created < System.currentTimeMillis()/1000) {
                                if(!questions.contains(addQuestion)) {
                                    questions.add(addQuestion);
                                }
                            }
                            Collections.sort(questions);
                            adapter.notifyDataSetChanged();
                            noQuestionsTxt.setVisibility(View.GONE);
                            manager.scrollToPosition(0);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
                if(questions.isEmpty()){
                    noQuestionsTxt.setVisibility(View.VISIBLE);
                }
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onRefresh() {
        swipeRefreshLayout.setRefreshing(true);
        manager.removeAllViews();
        ucitajPitanja();
    }

    @Override
    public void onBackPressed() {
        if(neodgovorenoPitanje == -1) {
            setResult(QUESTION_UNANSWERED);
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i("jedan","dva");
        if(requestCode == QUESTION_ANSWERED){
            //Log.i(data.getIntExtra("position", -1)+"ahaha","jedan");
            Log.i(resultCode+"", "hehe");
            if(resultCode == RESULT_OK){
                Log.i("jedan","tri");
                Log.i(data.getIntExtra("position", 0) +"", data.getIntExtra("points", 0)+"");
                int position = data.getIntExtra("position", 0);
                int points = data.getIntExtra("points", 0);
                Question question = questions.get(position);
                question.setPoints(points);
                adapter.notifyItemChanged(position, question);
            }
        }
    }
}
