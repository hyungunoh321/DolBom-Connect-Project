package com.siheung.careconnect.facilityadmin

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivityAdminNotificationBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class AdminNotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminNotificationBinding
    private var facilityId: String = ""
    private var selectedImageUri: Uri? = null
    private val children = mutableListOf<ChildNotifItem>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedImageUri = uri
        binding.ivImagePreview.setImageURI(uri)
        binding.ivImagePreview.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        facilityId = intent.getStringExtra("facility_id") ?: ""

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnRemoveImage.setOnClickListener { removeImage() }
        binding.btnSend.setOnClickListener { sendNotification() }

        loadChildren()
    }

    private fun removeImage() {
        selectedImageUri = null
        binding.ivImagePreview.setImageDrawable(null)
        binding.ivImagePreview.visibility = View.GONE
        binding.btnRemoveImage.visibility = View.GONE
    }

    private fun loadChildren() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (facilityId.isNotEmpty()) {
                        // 이 시설에 예약된 아동만 조회
                        SupabaseClientProvider.client.postgrest["reservations"]
                            .select(Columns.raw("id, children(id, name, parent_id)")) {
                                filter { eq("facility_id", facilityId) }
                            }
                            .decodeList<ReservationChildRow>()
                            .mapNotNull { it.children }
                            .distinctBy { it.id }
                    } else {
                        // facilityId 없으면 전체 아동 조회
                        SupabaseClientProvider.client.postgrest["children"]
                            .select()
                            .decodeList<ChildNotifItem>()
                    }
                }

                children.clear()
                children.addAll(result)

                if (children.isEmpty()) {
                    binding.spinnerChild.visibility = View.GONE
                    binding.tvNoChildren.visibility = View.VISIBLE
                } else {
                    val names = children.map { it.name }
                    val adapter = ArrayAdapter(
                        this@AdminNotificationActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerChild.adapter = adapter
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminNotificationActivity, "아동 목록 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun sendNotification() {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "메시지를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (children.isEmpty()) {
            Toast.makeText(this, "알림을 받을 아동이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        binding.btnSend.isEnabled = false

        lifecycleScope.launch {
            try {
                // 이미지 업로드 (선택된 경우)
                val imageUrl = selectedImageUri?.let { uploadImage(it) }

                // 최종 메시지 구성
                val fullText = buildString {
                    val selectedChild = children.getOrNull(binding.spinnerChild.selectedItemPosition)
                    if (selectedChild != null) append("[${selectedChild.name}] ")
                    append(message)
                    if (imageUrl != null) append("\n$imageUrl")
                }

                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["notices"].insert(
                        buildJsonObject {
                            put("text", fullText)
                            put("date", today)
                            put("is_read", false)
                        }
                    )
                }

                Toast.makeText(this@AdminNotificationActivity, "알림이 전송되었습니다", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@AdminNotificationActivity, "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
                binding.btnSend.isEnabled = true
            }
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
                val ext = contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
                val fileName = "${UUID.randomUUID()}.$ext"
                SupabaseClientProvider.client.storage["notice-images"].upload(fileName, bytes)
                SupabaseClientProvider.client.storage["notice-images"].publicUrl(fileName)
            } catch (e: Exception) {
                // Storage 버킷이 없거나 업로드 실패 시 이미지 없이 진행
                null
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}

@Serializable
private data class ReservationChildRow(
    val id: String = "",
    val children: ChildNotifItem? = null
)

@Serializable
data class ChildNotifItem(
    val id: String,
    val name: String,
    @SerialName("parent_id") val parentId: String? = null
)
