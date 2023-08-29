package com.petpal.swimmer_customer.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.petpal.swimmer_customer.data.model.Address
import com.petpal.swimmer_customer.data.model.User
import com.petpal.swimmer_customer.util.AutoLoginUtil

class CustomerUserRepository : UserRepository {
    private val mAuth: FirebaseAuth
    private val mDatabase: DatabaseReference
    private val mStorage:StorageReference

    init {
        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance().getReference("users")
        mStorage = FirebaseStorage.getInstance().getReference("profile_images")
    }
    //회원가입+db에 저장하는 메서드
    override fun addUser(user: User?): LiveData<Boolean?>? {
        val resultLiveData = MutableLiveData<Boolean?>()
        //firebase auth의 회원가입 메서드
        mAuth.createUserWithEmailAndPassword(user?.email!!, user.password!!)

            .addOnCompleteListener { task: Task<AuthResult?> ->
                if (task.isSuccessful) {
                    // 회원가입이 성공했을 때 Firebase Realtime Database에 유저 정보 저장
                    val firebaseUser = mAuth.currentUser
                    if (firebaseUser != null) {
                        //비밀번호는 db에 올리지 않는다.
                        user.password = null
                        user.uid=firebaseUser.uid
                        mDatabase.child((firebaseUser.uid).toString())
                            .setValue(user)
                            .addOnCompleteListener { dbTask: Task<Void?> ->
                                if (dbTask.isSuccessful) {
                                    resultLiveData.setValue(true)
                                } else {
                                    // DB 저장 실패시
                                    resultLiveData.setValue(false)
                                }
                            }
                    } else {
                        resultLiveData.setValue(false)
                    }
                } else {
                    // 회원가입 실패시
                    resultLiveData.setValue(false)
                }
            }
        return resultLiveData
    }

fun addAddressForUser(uid: String, address: Address): LiveData<Boolean?> {
    val resultLiveData = MutableLiveData<Boolean?>()
    val addressRef = mDatabase.child(uid).child("address").push() // Generates a unique key for the new address

    address.addressIdx = addressRef.key // Set the generated key as the addressIdx for the Address object

    addressRef.setValue(address)
        .addOnCompleteListener { task: Task<Void?> ->
            if (task.isSuccessful) {
                resultLiveData.setValue(true)
            } else {
                resultLiveData.setValue(false)
            }
        }
    return resultLiveData
}
    fun getAllAddressesForUser(uid: String): LiveData<List<Address>> {
        val addressesLiveData = MutableLiveData<List<Address>>()
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("address")

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val addressList = mutableListOf<Address>()
                for (addressSnapshot in dataSnapshot.children) {
                    val address = addressSnapshot.getValue(Address::class.java)
                    address?.let { addressList.add(it) }
                }
                addressesLiveData.value = addressList
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle errors here, if necessary
            }
        })

        return addressesLiveData
    }
    //배송지 삭제
    fun deleteAddressForUser(uid: String, addressIdx: String): LiveData<Boolean?> {
        val resultLiveData = MutableLiveData<Boolean?>()
        val addressRef = mDatabase.child(uid).child("address").child(addressIdx)
        addressRef.removeValue()
            .addOnCompleteListener { task: Task<Void?> ->
                if (task.isSuccessful) {
                    resultLiveData.setValue(true)
                } else {
                    resultLiveData.setValue(false)
                }
            }
        return resultLiveData
    }


    //로그인 메서드
    override fun signInUser(email: String, password: String): LiveData<Boolean?> {
        val resultLiveData = MutableLiveData<Boolean>()
        //firebase auth의 로그인 메서드
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task: Task<AuthResult?> ->
                if (task.isSuccessful) {
                    resultLiveData.setValue(true)
                } else {
                    resultLiveData.setValue(false)
                }
            }
        return resultLiveData
    }



    //이메일 중복체크
    override fun checkEmailDuplicated(email: String?): LiveData<Boolean> {

        val emailExists = MutableLiveData<Boolean>()
        Log.d("targetEmail",email!!)
        //받아온 이메일과 user의 자식 노드들의 email 속성을 검사
        mDatabase.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        emailExists.setValue(true)
                    } else {
                        emailExists.setValue(false)
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    emailExists.value = false
                }
            })
        return emailExists
    }
    //    @Override
    //    public LiveData<Boolean> modifyUser(String userIdx, String email, String password) {
    //        MutableLiveData<Boolean> result = new MutableLiveData<>();
    //
    //        //serIdx에 해당하는 사용자의 정보를 가져옴
    //        mDatabase.child(userIdx).addListenerForSingleValueEvent(new ValueEventListener() {
    //            @Override
    //            public void onDataChange(DataSnapshot dataSnapshot) {
    //                User user = dataSnapshot.getValue(User.class);
    //                if (user != null) {
    //                    // 가져온 사용자 정보를 수정
    //                    user.setEmail(email);
    //                    user.setPassword(password);
    //
    //                    // 수정된 사용자 정보를 데이터베이스에 저장
    //                    mDatabase.child(userIdx).setValue(user).addOnCompleteListener(task -> {
    //                        result.setValue(task.isSuccessful());
    //                    });
    //                } else {
    //                    result.setValue(false); // userIdx에 일치하는 사용자 정보가 없음
    //                }
    //            }
    //
    //            @Override
    //            public void onCancelled(DatabaseError databaseError) {
    //                result.setValue(false);
    //            }
    //        });
    //
    //        return result;
    //    }

    //유저 삭제
    override fun deleteUser(userIdx: String?): LiveData<Boolean?>? {
        val result = MutableLiveData<Boolean?>()
        mDatabase.child(userIdx!!).removeValue()
            .addOnCompleteListener { task: Task<Void?> -> result.setValue(task.isSuccessful) }
        return result
    }

    //이메일 찾기 메서드
    override fun findEmailbyInfo(nickname: String?, phoneNumber: String?): LiveData<User?>? {
        val result = MutableLiveData<User?>()

        //핸드폰 번호 일치하는 노드 찾음
        mDatabase.orderByChild("phoneNumber").equalTo(phoneNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (childSnapshot in dataSnapshot.children) {
                        val user = childSnapshot.getValue(User::class.java)
                        if (user != null && user.nickName == nickname) {
                            // 사용자의 폰 번호와 닉네임이 모두 일치하는 경우 해당 사용자 정보를 설정합니다.
                            result.value = user
                            return
                        }
                    }
                    result.value = null // 일치하는 사용자를 찾지 못했을 때 null을 설정합니다.
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    result.value = null
                }
            })
        return result
    }

    //비밀번호 재설정 메서드
    override fun resetPassword(email: String?, phoneNumber: String?): LiveData<Boolean?>? {
        val resultLiveData = MutableLiveData<Boolean?>()

        // DB에서 이메일로 users 하위의 자식 노드들에서 email 속성을 검사
        mDatabase.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (userSnapshot in dataSnapshot.children) {
                            val user = userSnapshot.getValue(
                                User::class.java
                            )
                            if (user != null && user.phoneNumber == phoneNumber) {
                                // 이메일과 핸드폰 번호가 일치하는 사용자를 찾음
                                FirebaseAuth.getInstance().sendPasswordResetEmail(email!!)
                                    .addOnCompleteListener { task: Task<Void?> ->
                                        if (task.isSuccessful) {
                                            resultLiveData.setValue(true)
                                        } else {
                                            resultLiveData.setValue(false)
                                        }
                                    }
                                return
                            }
                        }
                    }
                    resultLiveData.value = false // 일치하는 사용자를 찾지 못함
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    resultLiveData.value = false
                }
            })
        return resultLiveData
    }
    //현재 사용자를 얻어오는 메서드


    fun getCurrentUser(): LiveData<User?>? {
        val result = MutableLiveData<User?>()
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        mDatabase.orderByChild("uid").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (childSnapshot in dataSnapshot.children) {
                        val user = childSnapshot.getValue(User::class.java)
                        if (user != null) {

                            result.value = user
                            return
                        }
                    }
                    result.value = null
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    result.value = null
                }
            })
        return result
    }
    fun verifyPassword(password: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        val currentUser = mAuth.currentUser

        if (currentUser != null) {
            val credential = EmailAuthProvider.getCredential(currentUser.email!!, password)
            currentUser.reauthenticate(credential).addOnCompleteListener { task ->
                result.value = task.isSuccessful
            }
        } else {
            result.value = false
        }

        return result
    }
    fun uploadImageToFirebase(uid: String, uri: Uri): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        val storageRef = mStorage.child(uid)
        storageRef.putFile(uri)
            .addOnSuccessListener {
                result.value = true
            }
            .addOnFailureListener {
                result.value = false
            }
        return result
    }

    fun loadProfileImage(uid: String): LiveData<Uri?> {
        val result = MutableLiveData<Uri?>()
        val storageRef = mStorage.child(uid)
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            result.value = uri
        }.addOnFailureListener {
            result.value = null
        }
        return result
    }
    //로그아웃 메서드
    fun signOut() {
        //firebase auth의 로그아웃 메서드
        FirebaseAuth.getInstance().signOut()
    }
    //자동 로그인 설정
    fun setAutoLogin(context: Context, value: Boolean) {
        AutoLoginUtil.setAutoLogin(context, value)
    }
    //비밀번호 변경
    fun modifyPassword(currentPassword: String, newPassword: String): LiveData<Boolean?> {
        val result = MutableLiveData<Boolean?>()
        val currentUser = mAuth.currentUser

        if (currentUser != null) {
            // 현재 비밀번호를 검증하기 위해 Credential 객체를 생성
            val credential = EmailAuthProvider.getCredential(currentUser.email!!, currentPassword)
            currentUser.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    currentUser.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                        result.value = true
                    }
                } else {
                    Log.e("ModifyPassword", "Reauthentication failed: ${reauthTask.exception?.message}")
                    result.value = false
                }
            }
        } else {
            result.value = false
        }

        return result
    }
    fun ModifyUserInfo(user: User): LiveData<Boolean?> {
        val resultLiveData = MutableLiveData<Boolean?>()
        val currentUser = mAuth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid

            // 업데이트할 사용자 정보 필드들을 맵 형태로 생성
            val updatedUserInfoMap = mutableMapOf<String, Any?>()
            updatedUserInfoMap["swimExp"] = user.swimExp
            updatedUserInfoMap["nickName"] = user.nickName
            updatedUserInfoMap["phoneNumber"] = user.phoneNumber

            // 데이터베이스에서 현재 사용자의 정보 노드에 업데이트
            mDatabase.child(uid).updateChildren(updatedUserInfoMap)
                .addOnCompleteListener { updateTask: Task<Void?> ->
                    if (updateTask.isSuccessful) {
                        resultLiveData.value = true
                    } else {
                        resultLiveData.value = false
                    }
                }
        } else {
            resultLiveData.value = false
        }

        return resultLiveData
    }
    fun withdrawal(){
       // FirebaseAuth.getInstance().
    }

}
