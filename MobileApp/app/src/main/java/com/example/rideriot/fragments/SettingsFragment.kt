package com.example.rideriot.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.rideriot.R

class SettingsFragment : Fragment() {

    private lateinit var etPhone1: EditText
    private lateinit var etPhone2: EditText
    private lateinit var etPhone3: EditText
    private lateinit var etSosMessage: EditText
    private lateinit var btnSave: Button

    //O ficheiro das sharedpreferences onde o Android vai guardar as configurações
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Start UI
        etPhone1 = view.findViewById(R.id.etPhone1)
        etPhone2 = view.findViewById(R.id.etPhone2)
        etPhone3 = view.findViewById(R.id.etPhone3)
        etSosMessage = view.findViewById(R.id.etSosMessage)
        btnSave = view.findViewById(R.id.btnSaveSettings)

        //Criar/abrir o ficheiro local de configurações
        sharedPrefs = requireActivity().getSharedPreferences("RiderIotSettings", Context.MODE_PRIVATE)

        loadSettings()

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        //Se não houver nada gravado, devolve uma string vazia ("")
        etPhone1.setText(sharedPrefs.getString("phone1", ""))
        etPhone2.setText(sharedPrefs.getString("phone2", ""))
        etPhone3.setText(sharedPrefs.getString("phone3", ""))

        //Mensagem por defeito se o utilizador ainda não tiver configurado nada
        val defaultMsg = "Tive uma queda com a mota. Preciso de ajuda nesta localização:"
        etSosMessage.setText(sharedPrefs.getString("sos_message", defaultMsg))
    }

    private fun saveSettings() {
        val editor = sharedPrefs.edit()

        //Apanha o texto das caixas e grava nas keys
        editor.putString("phone1", etPhone1.text.toString().trim())
        editor.putString("phone2", etPhone2.text.toString().trim())
        editor.putString("phone3", etPhone3.text.toString().trim())
        editor.putString("sos_message", etSosMessage.text.toString().trim())

        //O .apply() grava de forma assíncrona (não bloqueia a app)
        editor.apply()

        Toast.makeText(requireContext(), "Definições Guardadas", Toast.LENGTH_SHORT).show()
    }
}