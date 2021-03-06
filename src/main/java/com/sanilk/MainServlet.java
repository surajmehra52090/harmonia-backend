package com.sanilk;

import com.sanilk.hibernate_classes.comment.Comment;
import com.sanilk.hibernate_classes.comment.CommentHandler;
import com.sanilk.hibernate_classes.genre.Genre;
import com.sanilk.hibernate_classes.notification.Notification;
import com.sanilk.hibernate_classes.notification.NotificationHandler;
import com.sanilk.hibernate_classes.playlist.Playlist;
import com.sanilk.hibernate_classes.playlist.PlaylistHandler;
import com.sanilk.hibernate_classes.song.Song;
import com.sanilk.hibernate_classes.user.User;
import com.sanilk.hibernate_classes.user.UserHandler;
import com.sanilk.requests.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;


public class MainServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        JSONParser jsonParser=new JSONParser();

        String requestText=new DataInputStream(req.getInputStream()).readUTF();
        MyRequest request=jsonParser.parse(requestText);
        DataOutputStream dos=new DataOutputStream(resp.getOutputStream());
        switch (request.requestType){
            case CreateUserRequest.REQUEST_TYPE:

                UserHandler userHandler=new UserHandler();
                CreateUserRequest createUserRequest=(CreateUserRequest)request;
                userHandler.saveUser(new User(createUserRequest.getUserName(),
                        createUserRequest.getPassword(),
                        createUserRequest.getEmail()));

                JSONObject jsonObject=new JSONObject();
                jsonObject.put("response_type", "CREATE_USER_RESPONSE");
                jsonObject.put("successful", true);
                jsonObject.put("error_code", -1);
                jsonObject.put("error_details", "none");

                String response=jsonObject.toString();
                dos.writeUTF(response);

                break;

            case AuthenticateRequest.REQUEST_TYPE:
                AuthenticateRequest authenticateRequest=(AuthenticateRequest)request;

                JSONObject authJSONObject=new JSONObject();
                authJSONObject.put("response_type", "AUTHENTICATE_RESPONSE");
                authJSONObject.put("successful", true);
                authJSONObject.put("error_code", -1);
                authJSONObject.put("error_details", "none");

                UserHandler userHandler2=new UserHandler();
                User user=userHandler2.getUser(authenticateRequest.getUserName());
                if(user!=null && user.getPassword().equals(authenticateRequest.getPassword())){
                    authJSONObject.put("authentic", true);
                }else{
                    authJSONObject.put("authentic", false);
                }

                String authResponse=authJSONObject.toString();
                dos.writeUTF(authResponse);

                break;

            case CreatePlaylistRequest.REQUEST_TYPE:
                CreatePlaylistRequest createPlaylistRequest=(CreatePlaylistRequest)request;

                PlaylistHandler playlistHandler=new PlaylistHandler();

                String playlistName=createPlaylistRequest.getPlaylistName();
                String genres="temp_genres";
                int points=-1;

                Set<Song> songsArr=new HashSet<>();
                CreatePlaylistRequest.Song[] songs=createPlaylistRequest.getSongs();

                //Do some kind fo check to make sure songs arent repeated in db
                Playlist playlist=new Playlist(playlistName, genres, points, new HashSet<Song>());
                playlist.setCreator(
                        new UserHandler().getUser(createPlaylistRequest.getUserName())
                );

                for(int i=0;i<songs.length;i++){
                    String tempName=songs[i].getName();
                    String tempArtist=songs[i].getArtist();
                    String id=songs[i].getLink();
                    Song temp=new Song(tempName, id, tempArtist, new HashSet<Genre>());
                    Set<Playlist> playlists=new HashSet<>();
                    playlists.add(playlist);
                    temp.playlists=playlists;

                    Set<Genre> genresArr=new HashSet<>();
                    CreatePlaylistRequest.Song.Genre[] tempGenres=
                             songs[i].getGenres();
                    for(int j=0;j<tempGenres.length;j++){
                        Genre temptempgenre=new Genre(
                                tempGenres[j].getName(),
                                temp
                        );
                        genresArr.add(temptempgenre);
                    }

                    temp.genres=genresArr;
                    songsArr.add(temp);
                }

                //Handle notifications
                NotificationHandler notificationHandler=new NotificationHandler();
                notificationHandler.newPlaylistCreatedNotification(playlist);

                playlist.songSet=songsArr;
                playlistHandler.savePlaylist(playlist);

                JSONObject createPlaylistJSONObject=new JSONObject();
                //Write response here

                String createPlaylistResponse=createPlaylistJSONObject.toString();
                dos.writeUTF(createPlaylistResponse);

                break;


            case GetRandomPlaylistRequest.REQUEST_TYPE:
                GetRandomPlaylistRequest getRandomPlaylistRequest=
                        (GetRandomPlaylistRequest)request;

                PlaylistHandler playlistHandlerForRandomPlaylist=new PlaylistHandler();
                Playlist randomPlaylist=playlistHandlerForRandomPlaylist
                        .getRandomPlaylist();

                if(randomPlaylist!=null) {

                    JSONObject jsonResponseForRandomPlaylist = new JSONObject();
                    jsonResponseForRandomPlaylist.put("response_type", "GET_RANDOM_PLAYLIST_RESPONSE");
                    jsonResponseForRandomPlaylist.put("upvotes", 10);
                    jsonResponseForRandomPlaylist.put("downvotes", 1);
                    jsonResponseForRandomPlaylist.put("username", randomPlaylist.getCreator().getName());
                    jsonResponseForRandomPlaylist.put("name", randomPlaylist.getName());
                    jsonResponseForRandomPlaylist.put("songs_count", randomPlaylist.songSet.size());

                    JSONArray allSongsArray = new JSONArray();
                    for (Song s : randomPlaylist.songSet) {
                        JSONObject tempObject = new JSONObject();
                        tempObject.put("link", s.getSongId());
                        tempObject.put("name", s.getName());
                        tempObject.put("artist", s.getArtist());
                        tempObject.put("genres_count", s.genres.size());

                        JSONArray allGenresArray = new JSONArray();
                        for (Genre g : s.genres) {
                            JSONObject temptempObject = new JSONObject();
                            temptempObject.put("name", g.getName());

                            allGenresArray.put(temptempObject);
                        }

                        tempObject.put("genres", allGenresArray);
                        allSongsArray.put(tempObject);
                    }

                    jsonResponseForRandomPlaylist.put("songs", allSongsArray);

                    //Add stuff for comments
                    dos.writeUTF(jsonResponseForRandomPlaylist.toString());

                }else{
                    dos.writeUTF("No playlists");
                }

                break;

            case GetCommentsByPlaylistIdRequest.REQUEST_TYPE:
                GetCommentsByPlaylistIdRequest getCommentsByPlaylistIdRequest =(GetCommentsByPlaylistIdRequest)request;
                CommentHandler commentHandlerForGetCommentsByPlaylist=
                        new CommentHandler();
                List<Comment> comments=commentHandlerForGetCommentsByPlaylist.getComments(
                        getCommentsByPlaylistIdRequest.getPlaylistId()
                );

                JSONObject responseObjectForGetComments=new JSONObject();
                responseObjectForGetComments.put("response_type", "GET_COMMENTS_RESPONSE");
                responseObjectForGetComments.put("playlist_id", getCommentsByPlaylistIdRequest.getPlaylistId());

                JSONArray jsonArray=new JSONArray();
                for(Comment c:comments){
                    JSONObject jsonObjectTemp=new JSONObject();
                    jsonObjectTemp.put("by", c.getUser().getId());
                    jsonObjectTemp.put("text", c.getText());
                    jsonObjectTemp.put("date", c.getDate());

                    jsonArray.put(jsonObjectTemp);
                }

                responseObjectForGetComments.put("comments", jsonArray);

                dos.writeUTF(
                        responseObjectForGetComments.toString()
                );

                break;

            case AddCommentRequest.REQUEST_TYPE:
                AddCommentRequest addCommentRequest=(AddCommentRequest)request;

                CommentHandler commentHandler=new CommentHandler();
                PlaylistHandler playlistHandlerForAddingComments=new PlaylistHandler();
                UserHandler userHandlerForAddingComments=new UserHandler();

                Playlist p=playlistHandlerForAddingComments.getPlaylist(addCommentRequest.playlistId);
                User u=userHandlerForAddingComments.getUser(addCommentRequest.username);

                commentHandler.newComment(
                        new Comment(
                                addCommentRequest.text,
                                new Date().toString(),
                                p,
                                u
                        )
                );

                dos.writeUTF("kuch aaega yahan");

                break;

            case PlaylistReactionRequest.REQUEST_TYPE:
                PlaylistReactionRequest playlistReactionRequest=
                        (PlaylistReactionRequest)request;

                PlaylistHandler playlistHandlerForPlaylistReaction=
                        new PlaylistHandler();
                int playlistId=playlistReactionRequest.getPlaylistId();
                switch (playlistReactionRequest.getReaction()){
                    case "upvote":
                        playlistHandlerForPlaylistReaction.upvotePlaylist(playlistId);
                        break;
                    case "downvote":
                        playlistHandlerForPlaylistReaction.downvotePlaylist(playlistId);
                        break;
                }
                dos.writeUTF("playlist reaction request response not done");
                break;

            case GetNotificationsRequest.REQUEST_TYPE:
                GetNotificationsRequest getNotificationsRequest=
                        (GetNotificationsRequest)request;

                NotificationHandler notificationHandlerForGetNotification=
                        new NotificationHandler();
                List<Notification> notifications=notificationHandlerForGetNotification
                        .getNotifications(getNotificationsRequest.getUsername());
                dos.writeUTF("get notification request response not done");

                break;

//            case NewSongRequest.REQUEST_TYPE:
//                NewSongRequest newSongRequest=(NewSongRequest)request;
//
//                JSONObject newSongJSONObject=new JSONObject();
//
//                SongHandler songHandler=new SongHandler();
//
//                HashSet<Genre> genres2=new HashSet<>();
//                for(NewSongRequest.Genre g:newSongRequest.getGenres()) {
//                    Genre g2=new Genre(g.getName(), s);
//                    genres2.add(g2);
//                }
//                Song s=new Song(
//                        newSongRequest.getName(),
//                        newSongRequest.getId(),
//                        newSongRequest.getArtist(),
//                        playlist,
//                        genres2
//                );
//                s.genres=genres2;
//                songHandler.saveSong(s);
//
//                dos.writeUTF(newSongJSONObject.toString());
//                break;
            default:
                dos.writeUTF("Uhmm..... there was supposed to be something here. but its not right now. so... uhmm. well, this is awkward");
        }
    }
}
