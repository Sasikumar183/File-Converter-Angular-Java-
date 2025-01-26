import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [CommonModule, FormsModule, HttpClientModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  username: string = '';
  fileData: any = null;  // Holds the file data
  userId: string = ''; // To store the user ID
  uploadedFiles: string[] = ["You have not uploaded any files yet"]; // Holds the list of uploaded files
  selectedFile: string = ''; // The file selected for conversion
  selectedConversion: string = ''; // The conversion type selected by the user
  downloadLink: string = ''; // The link to download the converted file

  constructor(private route: ActivatedRoute, private http: HttpClient) {}

  ngOnInit() {
    
    const userId = this.route.snapshot.queryParamMap.get('id');
    console.log(userId);
    this.userId = userId || ''; //Set userId 

    if (userId) {
      const servletUrl = `http://localhost:8080/DemoAng/HomeServlet?id=${userId}`;
      this.http.get<{ username: string }>(servletUrl).subscribe(
        (response) => {
          this.username = response.username;
        },
        (error) => {
          console.error('Error fetching username', error);
        }
      );

      this.fetchUploadedFiles(userId);
    }
  }

  onFileChange(event: any) {
    const file = event.target.files[0]; 
    if (file) {
      this.fileData = file; 
    }
  }

  onSubmit() {
    if (!this.fileData) {
      console.error('No file selected!');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.fileData);
    formData.append('user_id', this.userId); 

    const uploadUrl = 'http://localhost:8080/DemoAng/FileUploadServlet'; 

    this.http.post(uploadUrl, formData).subscribe(
      (response) => {
        console.log('File uploaded successfully:', response);
        alert("File uploaded successfully");

        this.fetchUploadedFiles(this.userId);
      },
      (error) => {
        console.error('Error uploading file:', error);
        alert("File not uploaded");
      }
    );
  }

  fetchUploadedFiles(userId: string) {
    const getFilesUrl = `http://localhost:8080/DemoAng/GetUploadedFilesServlet?id=${userId}`;
    this.http.get<{ files: string[] }>(getFilesUrl).subscribe(
      (response) => {
        this.uploadedFiles = response.files || ["You have not uploaded any files yet"];
        console.log(this.uploadedFiles);
      },
      (error) => {
        console.error('Error fetching files', error);
      }
    );
  }

  onFileSelect(file: string) {
    this.selectedFile = file;
    console.log('Selected file:', this.selectedFile);
  }

  onConvert() {
    if (!this.selectedFile || !this.selectedConversion) {
      alert('Please select a file and a conversion type!');
      return;
    }
  
    const fileName = this.selectedFile.toLowerCase();
    const fileExtension = fileName.split('.').pop();
  
    switch (this.selectedConversion) {
      case 'csv2json':
        if (fileExtension !== 'csv') {
          alert('Please select a valid CSV file for CSV to JSON conversion.');
          return;
        }
        break;
      case 'json2csv':
        if (fileExtension !== 'json') {
          alert('Please select a valid JSON file for JSON to CSV conversion.');
          return;
        }
        break;
      case 'csv2xlsx':
        if (fileExtension !== 'csv') {
          alert('Please select a valid CSV file for CSV to XLSX conversion.');
          return;
        }
        break;
      case 'xlsx2csv':
        if (fileExtension !== 'xlsx') {
          alert('Please select a valid XLSX file for XLSX to CSV conversion.');
          return;
        }
        break;
      case 'xlsx2json':
        if (fileExtension !== 'xlsx') {
          alert('Please select a valid XLSX file for XLSX to JSON conversion.');
          return;
        }
        break;
      case 'json2xlsx':
        if (fileExtension !== 'json') {
          alert('Please select a valid JSON file for JSON to XLSX conversion.');
          return;
        }
        break;
      default:
        alert('Unsupported conversion type: ' + this.selectedConversion);
        return;
    }
  
    let conversionUrl = '';
  
    switch (this.selectedConversion) {
      case 'csv2json':
        conversionUrl = `http://localhost:8080/DemoAng/CsvToJsonServlet`;
        break;
      case 'json2csv':
        conversionUrl = `http://localhost:8080/DemoAng/JsonToCsvServlet`;
        break;
      case 'csv2xlsx':
        conversionUrl = `http://localhost:8080/DemoAng/CsvToXlsxServlet`;
        break;
      case 'xlsx2csv':
        conversionUrl = `http://localhost:8080/DemoAng/XlsxToCsvServlet`;
        break;
      case 'xlsx2json':
        conversionUrl = `http://localhost:8080/DemoAng/XlsxToJsonServlet`;
        break;
      case 'json2xlsx':
        conversionUrl = `http://localhost:8080/DemoAng/JsonToXlsxServlet`;
        break;
      default:
        alert('Unsupported conversion type: ' + this.selectedConversion);
        return;
    }
  
    conversionUrl += `?filename=${encodeURIComponent(this.selectedFile)}&userId=${encodeURIComponent(this.userId)}`;
  
    const formData = new FormData();
    formData.append('file', this.selectedFile);
    formData.append('conversionType', this.selectedConversion);
    formData.append('userId', this.userId);
  
    this.http.post<{ downloadLink: string }>(conversionUrl, formData).subscribe(
      (response) => {
        console.log('File converted successfully:', response);
        if (response.downloadLink) {
          this.downloadLink = response.downloadLink;
          alert(
            'File converted successfully'
          );
          this.fetchUploadedFiles(this.userId);
        }
      },
      (error) => {
        console.error('Error converting file:', error);
        alert('Error during conversion: ' + error.message);
      }
    );
  }
  

    
}     